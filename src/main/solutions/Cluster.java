package jug.berlin;

import com.sun.tools.doclets.formats.html.SourceToHTMLConverter;
import org.infinispan.Cache;
import org.infinispan.commons.api.functional.EntryView;
import org.infinispan.commons.api.functional.EntryView.ReadEntryView;
import org.infinispan.commons.api.functional.EntryView.WriteEntryView;
import org.infinispan.commons.api.functional.FunctionalMap;
import org.infinispan.commons.api.functional.FunctionalMap.ReadOnlyMap;
import org.infinispan.commons.api.functional.FunctionalMap.ReadWriteMap;
import org.infinispan.commons.api.functional.FunctionalMap.WriteOnlyMap;
import org.infinispan.commons.api.functional.Listeners;
import org.infinispan.commons.api.functional.Listeners.ReadWriteListeners.ReadWriteListener;
import org.infinispan.commons.api.functional.MetaParam;
import org.infinispan.commons.api.functional.MetaParam.MetaLifespan;
import org.infinispan.commons.api.functional.Traversable;
import org.infinispan.commons.marshall.MarshallableFunctions;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.functional.impl.FunctionalMapImpl;
import org.infinispan.functional.impl.ReadOnlyMapImpl;
import org.infinispan.functional.impl.ReadWriteMapImpl;
import org.infinispan.functional.impl.WriteOnlyMapImpl;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

public class Cluster {

   public static void main(String[] args) throws Exception {
      Cluster.<String, String>withCluster((fmap1, fmap2) -> {
         WriteOnlyMap<String, String> woMap1 = WriteOnlyMapImpl.create(fmap1);
         WriteOnlyMap<String, String> woMap2 = WriteOnlyMapImpl.create(fmap2);
         ReadOnlyMap<String, String> roMap1 = ReadOnlyMapImpl.create(fmap1);
         ReadOnlyMap<String, String> roMap2 = ReadOnlyMapImpl.create(fmap2);
         ReadWriteMap<String, String> rwMap1 = ReadWriteMapImpl.create(fmap1);
         ReadWriteMap<String, String> rwMap2 = ReadWriteMapImpl.create(fmap2);

         //woMap2.listeners().onWrite(rview -> System.out.println("[node2-event] write: " + rview.find()));
         // BiConsumer<String, WriteEntryView<String>> f =
         //    (BiConsumer<String, WriteEntryView<String>> & Serializable) (v, wview) -> wview.set(v);
         CompletableFuture<Void> cluster0 = woMap1.eval("cluster", "cluster-value", MarshallableFunctions.setValueConsumer());
         CompletableFuture<String> cluster1 = cluster0.thenCompose($ -> roMap2.eval("cluster", ReadEntryView::get));
         futureGet(cluster1.thenAccept(value -> System.out.println("Value in node2: " + value)));

         rwMap2.listeners().add(new SampleReadWriteListener<>("node2"));
         rwMap1.listeners().add(new SampleReadWriteListener<>("node1"));

         Map<String, String> data = new HashMap<>();
         data.put("multi1-cluster", "multi1-value-cluster");
         data.put("multi2-cluster", "multi2-value-cluster");
         Traversable<Boolean> multi0 = rwMap2.evalMany(data,
            MarshallableFunctions.setValueMetasIfAbsentReturnBoolean(new MetaLifespan(5000)));
         multi0.forEach(System.out::println);
         CompletableFuture<String> multi1 = rwMap1.eval("multi1-cluster", "new-multi1-value-cluster",
            MarshallableFunctions.setValueReturnPrevOrNull());
         futureGet(multi1.thenAccept(System.out::println));
      });
   }

   private static class SampleReadWriteListener<K, V> implements ReadWriteListener<K, V> {
      final String nodeName;

      private SampleReadWriteListener(String nodeName) {
         this.nodeName = nodeName;
      }

      @Override
      public void onCreate(ReadEntryView<K, V> created) {
         System.out.println("[" + nodeName + "-event] created: " + created.find());
      }

      @Override
      public void onModify(ReadEntryView<K, V> before, ReadEntryView<K, V> after) {
         System.out.println("[" + nodeName + "-event] modified, before: " + before.find());
         System.out.println("[" + nodeName + "-event] modified, after: " + after.find());
      }
   };

   private static void futureGet(Future f) {
      try {
         f.get();
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   private static <K, V> void withCluster(BiConsumer<FunctionalMapImpl<K, V>, FunctionalMapImpl<K, V>> task) {
      ExecutorService exec = Executors.newCachedThreadPool();
      Future<EmbeddedCacheManager> f1 = exec.submit(Cluster::createClusteredCacheManager);
      Future<EmbeddedCacheManager> f2 = exec.submit(Cluster::createClusteredCacheManager);
      try {
         EmbeddedCacheManager cm1 = f1.get();
         EmbeddedCacheManager cm2 = f2.get();
         try {
            System.out.println(cm1.getMembers());
            System.out.println(cm2.getMembers());
            task.accept(FunctionalMapImpl.create(cm1.<K, V>getCache().getAdvancedCache()),
               FunctionalMapImpl.create(cm2.<K, V>getCache().getAdvancedCache()));
         } finally {
            if (cm1 != null) cm1.stop();
            if (cm2 != null) cm2.stop();
            exec.shutdown();
         }
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   private static EmbeddedCacheManager createClusteredCacheManager() {
      GlobalConfigurationBuilder global = GlobalConfigurationBuilder.defaultClusteredBuilder();
      global.transport().addProperty("configurationFile", "jbug-tcp1.xml");
      global.globalJmxStatistics().allowDuplicateDomains(true);
      ConfigurationBuilder builder = new ConfigurationBuilder();
      builder.clustering().cacheMode(CacheMode.REPL_SYNC);
      EmbeddedCacheManager cm = new DefaultCacheManager(global.build(), builder.build());
      cm.getCache();
      return cm;
   }

}
