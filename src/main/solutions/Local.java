package jug.berlin;

import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.commons.api.functional.EntryView;
import org.infinispan.commons.api.functional.EntryView.ReadEntryView;
import org.infinispan.commons.api.functional.EntryView.ReadWriteEntryView;
import org.infinispan.commons.api.functional.EntryView.WriteEntryView;
import org.infinispan.commons.api.functional.FunctionalMap;
import org.infinispan.commons.api.functional.FunctionalMap.ReadOnlyMap;
import org.infinispan.commons.api.functional.FunctionalMap.ReadWriteMap;
import org.infinispan.commons.api.functional.FunctionalMap.WriteOnlyMap;
import org.infinispan.commons.api.functional.MetaParam;
import org.infinispan.commons.api.functional.MetaParam.MetaLastUsed;
import org.infinispan.commons.api.functional.MetaParam.MetaLifespan;
import org.infinispan.commons.api.functional.Traversable;
import org.infinispan.functional.impl.FunctionalMapImpl;
import org.infinispan.functional.impl.ReadOnlyMapImpl;
import org.infinispan.functional.impl.ReadWriteMapImpl;
import org.infinispan.functional.impl.WriteOnlyMapImpl;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;

import javax.swing.text.html.Option;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

/**
 * Hello world!
 */
public class Local {

   public static void main(String[] args) throws Exception {
      EmbeddedCacheManager cacheManager = new DefaultCacheManager();

      ConcurrentMap<String, String> cache = cacheManager.getCache();
      String prev0 = cache.put("key", "value");
      System.out.println(prev0);
      String prev1 = cache.put("key", "value2");
      System.out.println(prev1);

      FunctionalMapImpl<String, String> fmap = FunctionalMapImpl.create(cacheManager.<String, String>getCache().getAdvancedCache());
      WriteOnlyMap<String, String> woMap = WriteOnlyMapImpl.create(fmap);
      ReadOnlyMap<String, String> roMap = ReadOnlyMapImpl.create(fmap);
      ReadWriteMap<String, String> rwMap = ReadWriteMapImpl.create(fmap);

      CompletableFuture<Void> crud0 = woMap.eval("crud", wview -> wview.set("crud-value"));
      CompletableFuture<String> crud1 = crud0.thenCompose($ -> roMap.eval("crud", ReadEntryView::get));
      CompletableFuture<Void> crud2 = crud1.thenAccept(System.out::println);
      CompletableFuture<Void> crud3 = crud2.thenCompose($ -> woMap.eval("crud", WriteEntryView::remove));
      CompletableFuture<Optional<String>> crud4 = crud3.thenCompose($ -> roMap.eval("crud", ReadEntryView::find));
      crud4.thenAccept(System.out::println).get();

      CompletableFuture<Boolean> cond0 = rwMap.eval("cond", "cond-value", (v, rwView) -> {
         if (!rwView.find().isPresent()) {
            rwView.set(v, new MetaLifespan(Duration.ofSeconds(3).toMillis()));
            return true;
         }
         return false;
      });
      CompletableFuture<Void> cond1 = cond0.thenAccept(System.out::println);
      //Thread.sleep(4000);
      CompletableFuture<Optional<MetaLifespan>> cond2 = cond1.thenCompose($ ->
         roMap.eval("cond", rview -> rview.findMetaParam(MetaLifespan.class)));
      cond2.thenAccept(lastUsed -> System.out.println("Lifespan: " + lastUsed)).get();

      Map<String, String> entries = new HashMap<>();
      entries.put("multi1", "multi1-value");
      entries.put("multi2", "multi2-value");
      entries.put("multi3", "multi3-value");
      CompletableFuture<Void> multi0 = woMap.evalMany(entries, (v, wview) -> wview.set(v));
      Set<String> keys = new HashSet<>(Arrays.asList("multi2", "multi3"));
      multi0.thenAccept($ -> {
         Traversable<ReadEntryView<String, String>> prevTraversable = rwMap.evalMany(keys, rwview -> {
            rwview.remove();
            return rwview;
         });

         prevTraversable
            .filter(view -> view.key().endsWith("2"))
            .forEach(System.out::println);
      }).get();
   }

}
