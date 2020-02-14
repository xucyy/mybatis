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

import java.util.Deque;
import java.util.LinkedList;

import org.apache.ibatis.cache.Cache;

/**
 * FIFO (first in, first out) cache decorator.
 * todo 利用先进先出的规则清理缓存，如果缓存项的个数已经达到上限，则会将缓存中最老（即最早进入缓存）的缓存项删除
 * @author Clinton Begin
 */
public class FifoCache implements Cache {

  //todo 底层被装饰的Cache对象
  private final Cache delegate;
  //todo 用于记录key进入缓存的先后顺序，使用的是LinkedList<Object> 类型的集合对象
  private final Deque<Object> keyList;
  //todo 记录了缓存项的上限，超过该值，就需要清理最老的缓存项
  private int size;

  public FifoCache(Cache delegate) {
    this.delegate = delegate;
    this.keyList = new LinkedList<>();
    //todo 默认记录1024个key
    this.size = 1024;
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  public void setSize(int size) {
    this.size = size;
  }

  @Override
  public void putObject(Object key, Object value) {
    //todo 检测并清理缓存
    cycleKeyList(key);
    //todo 调用底层封装的cache的putObject方法
    delegate.putObject(key, value);
  }

  @Override
  public Object getObject(Object key) {
    return delegate.getObject(key);
  }

  @Override
  public Object removeObject(Object key) {
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    delegate.clear();
    keyList.clear();
  }

  private void cycleKeyList(Object key) {
    //todo 记录key
    keyList.addLast(key);
    if (keyList.size() > size) {
      //todo  如果达到缓存上限，则清理最老的缓存项
      Object oldestKey = keyList.removeFirst();
      delegate.removeObject(oldestKey);
    }
  }

}
