/**
 *    Copyright 2009-2020 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.cache.decorators;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * The 2nd level cache transactional buffer.
 * <p>
 * This class holds all cache entries that are to be added to the 2nd level cache during a Session.
 * Entries are sent to the cache when commit is called or discarded if the Session is rolled back.
 * Blocking cache support has been added. Therefore any get() that returns a cache miss
 * will be followed by a put() so any lock associated with the key can be released.
 * todo 是CachingExecutor依赖的组件，主要用于保存在某个SqlSession的某个事务中需要向某个二级缓存中添加的缓存数据
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class TransactionalCache implements Cache {

  private static final Log log = LogFactory.getLog(TransactionalCache.class);

  //todo 底层封装的二级缓存所对应的Cache对象
  private final Cache delegate;
  //todo 当该字段为true时，则表示当前TransactionalCache不可查询，且提交事务时会将底层 Cache清空
  private boolean clearOnCommit;
  //todo 暂时记录添加到TransactionalCache中的数据，在事务提交时，会将其中的数据添加到二级缓存中
  private final Map<Object, Object> entriesToAddOnCommit;
  //todo 记录缓存未命中的CacheKey对象
  private final Set<Object> entriesMissedInCache;

  public TransactionalCache(Cache delegate) {
    this.delegate = delegate;
    this.clearOnCommit = false;
    this.entriesToAddOnCommit = new HashMap<>();
    this.entriesMissedInCache = new HashSet<>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  @Override
  public Object getObject(Object key) {
    // todo 首先查询底层的二级缓存
    Object object = delegate.getObject(key);
    if (object == null) {
      //todo 记录未命中的key
      entriesMissedInCache.add(key);
    }
    // todo 如果clearOnCommit为true,则当前TransactionalCache不可查询，返回null
    if (clearOnCommit) {
      return null;
    } else {
      //todo 返回从底层Cache中查询到的对象
      return object;
    }
  }

  //todo  先暂时保存到entriesToAddOnCommit集合中，在事务提交的时候，才会从entriesToAddOnCommit中put到二级缓存
  @Override
  public void putObject(Object key, Object object) {
    entriesToAddOnCommit.put(key, object);
  }

  @Override
  public Object removeObject(Object key) {
    return null;
  }

  @Override
  public void clear() {
    clearOnCommit = true;
    //todo 清空 entriesToAddonCommit集合
    entriesToAddOnCommit.clear();
  }

  public void commit() {
    //todo 判断是否需要清空二级缓存
    if (clearOnCommit) {
      delegate.clear();
    }
    //todo 将entriesToAddOnCommit集合中的数据保存到二级缓存
    flushPendingEntries();
    //todo 重置clearOnCommit为false，并清空entriesToAddOnCommit，entriesMissedInCache集合
    reset();
  }

  //todo 会将entriesMissedInCache集合中记录的缓存项从二级缓存中删除
  public void rollback() {
    unlockMissedEntries();
    reset();
  }

  private void reset() {
    clearOnCommit = false;
    entriesToAddOnCommit.clear();
    entriesMissedInCache.clear();
  }

  //将entriesToAddOnCommit集合中的数据保存到二级缓存
  private void flushPendingEntries() {
    for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
      delegate.putObject(entry.getKey(), entry.getValue());
    }
    for (Object entry : entriesMissedInCache) {
      if (!entriesToAddOnCommit.containsKey(entry)) {
        delegate.putObject(entry, null);
      }
    }
  }

  private void unlockMissedEntries() {
    for (Object entry : entriesMissedInCache) {
      try {
        delegate.removeObject(entry);
      } catch (Exception e) {
        log.warn("Unexpected exception while notifiying a rollback to the cache adapter. "
            + "Consider upgrading your cache adapter to the latest version. Cause: " + e);
      }
    }
  }

}
