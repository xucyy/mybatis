/**
 *    Copyright 2009-2019 the original author or authors.
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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;

/**
 * Simple blocking decorator
 *
 * Simple and inefficient version of EhCache's BlockingCache decorator.
 * It sets a lock over a cache key when the element is not found in cache.
 * This way, other threads will wait until this element is filled instead of hitting the database.
 * todo 缓存装饰器  是阻塞版本的，他会保证一个线程到数据库中查找指定key对应的数据
 * @author Eduardo Macarron
 *
 */
public class BlockingCache implements Cache {

  //todo 阻塞超时时长
  private long timeout;
  //todo 被装饰的Cache对象
  private final Cache delegate;
  //todo 每个key都有对应的 ReentrantLock对象
  private final ConcurrentHashMap<Object, ReentrantLock> locks;

  public BlockingCache(Cache delegate) {
    this.delegate = delegate;
    this.locks = new ConcurrentHashMap<>();
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
  public void putObject(Object key, Object value) {
    try {
      //todo 向缓存中添加缓存项
      delegate.putObject(key, value);
    } finally {
      //todo 释放key对应的锁，可以让别的线程来查询key对应的value了
      releaseLock(key);
    }
  }

  //todo 该方法的作用就是，一个线程过来get的时候，如果没有查询到值，则获取锁，防止别的线程还来获取这个空key。
  // 然后获取到锁的线程可以这时去数据库查值，查完了之后调用putObject方法，然后释放锁，其他线程就可以直接访问到key对应的value了
  @Override
  public Object getObject(Object key) {
    //todo 获取该key对应的锁
    acquireLock(key);
    //todo 查询key
    Object value = delegate.getObject(key);
    //todo 只有当value不为null时，才会释放锁，否则会一直持有锁
    if (value != null) {
      releaseLock(key);
    }
    return value;
  }

  @Override
  public Object removeObject(Object key) {
    // despite of its name, this method is called only to release locks
    releaseLock(key);
    return null;
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  private ReentrantLock getLockForKey(Object key) {
    return locks.computeIfAbsent(key, k -> new ReentrantLock());
  }

  //todo 获取key锁对应的锁
  private void acquireLock(Object key) {
    //todo 尝试获取锁，如果没有就创建一个
    Lock lock = getLockForKey(key);
    if (timeout > 0) {
      try {
        boolean acquired = lock.tryLock(timeout, TimeUnit.MILLISECONDS);
        if (!acquired) {
          throw new CacheException("Couldn't get a lock in " + timeout + " for the key " +  key + " at the cache " + delegate.getId());
        }
      } catch (InterruptedException e) {
        throw new CacheException("Got interrupted while trying to acquire lock for key " + key, e);
      }
    } else {
      lock.lock();
    }
  }

  private void releaseLock(Object key) {
    ReentrantLock lock = locks.get(key);
    //todo 锁是否被当前线程持有
    if (lock.isHeldByCurrentThread()) {
      //todo 释放锁
      lock.unlock();
    }
  }

  public long getTimeout() {
    return timeout;
  }

  public void setTimeout(long timeout) {
    this.timeout = timeout;
  }
}
