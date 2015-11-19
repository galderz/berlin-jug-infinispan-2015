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

public class Local {

   public static void main(String[] args) throws Exception {
      EmbeddedCacheManager cacheManager = new DefaultCacheManager();

      // HERE...

      FunctionalMapImpl<String, String> fmap = FunctionalMapImpl.create(cacheManager.<String, String>getCache().getAdvancedCache());
      WriteOnlyMap<String, String> woMap = WriteOnlyMapImpl.create(fmap);
      ReadOnlyMap<String, String> roMap = ReadOnlyMapImpl.create(fmap);
      ReadWriteMap<String, String> rwMap = ReadWriteMapImpl.create(fmap);

      // HERE

   }

}
