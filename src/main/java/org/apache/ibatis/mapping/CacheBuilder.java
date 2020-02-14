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
package org.apache.ibatis.mapping;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.builder.InitializingObject;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;
import org.apache.ibatis.cache.decorators.BlockingCache;
import org.apache.ibatis.cache.decorators.LoggingCache;
import org.apache.ibatis.cache.decorators.LruCache;
import org.apache.ibatis.cache.decorators.ScheduledCache;
import org.apache.ibatis.cache.decorators.SerializedCache;
import org.apache.ibatis.cache.decorators.SynchronizedCache;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

/**
 * @author Clinton Begin
 * todo Cache的建造者
 */
public class CacheBuilder {
  //todo Cache对象的唯一标识，一般情况下对应映射文件中的配置namespace
  private final String id;
  //todo Cache接口的真正实现类，默认值是前面介绍到PerpetualCache
  private Class<? extends Cache> implementation;
  //todo 装饰器集合，默认值包含LruCache.class
  private final List<Class<? extends Cache>> decorators;
  //todo Cache大小
  private Integer size;
  //todo 清理时间周期
  private Long clearInterval;
  //todo 是否可读写
  private boolean readWrite;
  //todo 其他配置信息
  private Properties properties;
  //todo 是否阻塞
  private boolean blocking;

  public CacheBuilder(String id) {
    this.id = id;
    this.decorators = new ArrayList<>();
  }

  public CacheBuilder implementation(Class<? extends Cache> implementation) {
    this.implementation = implementation;
    return this;
  }

  public CacheBuilder addDecorator(Class<? extends Cache> decorator) {
    if (decorator != null) {
      this.decorators.add(decorator);
    }
    return this;
  }

  public CacheBuilder size(Integer size) {
    this.size = size;
    return this;
  }

  public CacheBuilder clearInterval(Long clearInterval) {
    this.clearInterval = clearInterval;
    return this;
  }

  public CacheBuilder readWrite(boolean readWrite) {
    this.readWrite = readWrite;
    return this;
  }

  public CacheBuilder blocking(boolean blocking) {
    this.blocking = blocking;
    return this;
  }

  public CacheBuilder properties(Properties properties) {
    this.properties = properties;
    return this;
  }

  //todo 开始构建Cache对象
  public Cache build() {
    setDefaultImplementations();
    //todo 根据implementations指定的类型，通过反射获取参数为String类型的构造方法，并通过该构造方法创建Cache，初始化Cache对香
    Cache cache = newBaseCacheInstance(implementation, id);
    //todo 根据cache节点下配置的<property>来初始化Cache对象
    setCacheProperties(cache);
    // todo 检测cache对象的类型，如果是PerpetualCache类型，则为其添加到 decorations集合中
    if (PerpetualCache.class.equals(cache.getClass())) {
      for (Class<? extends Cache> decorator : decorators) {
        //todo 也就是将底层缓存类，放入到装饰器中，并实例化出来对应的缓存器
        cache = newCacheDecoratorInstance(decorator, cache);
        //todo 配置cache对象的属性
        setCacheProperties(cache);
      }
      //todo 添加mybatis中提供的标准装饰器
      cache = setStandardDecorators(cache);
    } else if (!LoggingCache.class.isAssignableFrom(cache.getClass())) {
      //todo  如果不是Logging的子类，则添加LoggingCache装饰器，也就是LoggingCache装饰了自定义的cache
      cache = new LoggingCache(cache);
    }
    return cache;
  }

  //todo 如果implementation为空，则配置一个默认的 PerpetualCache ，如果decorators为空，则配置一个默认的 LruCache
  private void setDefaultImplementations() {
    if (implementation == null) {
      implementation = PerpetualCache.class;
      if (decorators.isEmpty()) {
        decorators.add(LruCache.class);
      }
    }
  }
  //todo 添加mybatis中提供的标准装饰器 ，也就是利用装饰者模式，根据配置的值，将一个个缓存装饰器一个套一个，生成终极cache
  private Cache setStandardDecorators(Cache cache) {
    try {
      //todo 创建对象对应的MetaObject对象
      MetaObject metaCache = SystemMetaObject.forObject(cache);
      if (size != null && metaCache.hasSetter("size")) {
        metaCache.setValue("size", size);
      }
      //todo 检测是否指定了clearInterval,如果制定了 就添加ScheduledCache
      if (clearInterval != null) {
        cache = new ScheduledCache(cache);
        ((ScheduledCache) cache).setClearInterval(clearInterval);
      }
      //todo 是否只读，对应添加SerializedCache
      if (readWrite) {
        cache = new SerializedCache(cache);
      }
      //todo  默认添加 LoggingCache 和SynchronizedCache
      cache = new LoggingCache(cache);
      cache = new SynchronizedCache(cache);
      //todo  是否阻塞，对应添加Blocking装饰器
      if (blocking) {
        cache = new BlockingCache(cache);
      }
      return cache;
    } catch (Exception e) {
      throw new CacheException("Error building standard cache decorators.  Cause: " + e, e);
    }
  }
  //todo 根据cache节点下配置的<property>来初始化Cache对象
  private void setCacheProperties(Cache cache) {
    if (properties != null) {
      //todo cache对应的常见MetaObject对象
      MetaObject metaCache = SystemMetaObject.forObject(cache);
      for (Map.Entry<Object, Object> entry : properties.entrySet()) {
        //todo 配置项的名称，即Cache对应的属性名称
        String name = (String) entry.getKey();
        //todo 配置项的值，即Caceh对应的属性值
        String value = (String) entry.getValue();
        //todo 检测cache是否有该属性对应的setter方法
        if (metaCache.hasSetter(name)) {
          //todo 获取该属性的类型
          Class<?> type = metaCache.getSetterType(name);
          //todo 进行类型转换，并赋值操作
          if (String.class == type) {
            metaCache.setValue(name, value);
          } else if (int.class == type
              || Integer.class == type) {
            metaCache.setValue(name, Integer.valueOf(value));
          } else if (long.class == type
              || Long.class == type) {
            metaCache.setValue(name, Long.valueOf(value));
          } else if (short.class == type
              || Short.class == type) {
            metaCache.setValue(name, Short.valueOf(value));
          } else if (byte.class == type
              || Byte.class == type) {
            metaCache.setValue(name, Byte.valueOf(value));
          } else if (float.class == type
              || Float.class == type) {
            metaCache.setValue(name, Float.valueOf(value));
          } else if (boolean.class == type
              || Boolean.class == type) {
            metaCache.setValue(name, Boolean.valueOf(value));
          } else if (double.class == type
              || Double.class == type) {
            metaCache.setValue(name, Double.valueOf(value));
          } else {
            throw new CacheException("Unsupported property type for cache: '" + name + "' of type " + type);
          }
        }
      }
    }
    if (InitializingObject.class.isAssignableFrom(cache.getClass())) {
      try {
        ((InitializingObject) cache).initialize();
      } catch (Exception e) {
        throw new CacheException("Failed cache initialization for '"
          + cache.getId() + "' on '" + cache.getClass().getName() + "'", e);
      }
    }
  }

  private Cache newBaseCacheInstance(Class<? extends Cache> cacheClass, String id) {
    Constructor<? extends Cache> cacheConstructor = getBaseCacheConstructor(cacheClass);
    try {
      return cacheConstructor.newInstance(id);
    } catch (Exception e) {
      throw new CacheException("Could not instantiate cache implementation (" + cacheClass + "). Cause: " + e, e);
    }
  }

  private Constructor<? extends Cache> getBaseCacheConstructor(Class<? extends Cache> cacheClass) {
    try {
      return cacheClass.getConstructor(String.class);
    } catch (Exception e) {
      throw new CacheException("Invalid base cache implementation (" + cacheClass + ").  "
        + "Base cache implementations must have a constructor that takes a String id as a parameter.  Cause: " + e, e);
    }
  }

  private Cache newCacheDecoratorInstance(Class<? extends Cache> cacheClass, Cache base) {
    Constructor<? extends Cache> cacheConstructor = getCacheDecoratorConstructor(cacheClass);
    try {
      return cacheConstructor.newInstance(base);
    } catch (Exception e) {
      throw new CacheException("Could not instantiate cache decorator (" + cacheClass + "). Cause: " + e, e);
    }
  }

  private Constructor<? extends Cache> getCacheDecoratorConstructor(Class<? extends Cache> cacheClass) {
    try {
      return cacheClass.getConstructor(Cache.class);
    } catch (Exception e) {
      throw new CacheException("Invalid cache decorator (" + cacheClass + ").  "
        + "Cache decorators must have a constructor that takes a Cache instance as a parameter.  Cause: " + e, e);
    }
  }
}
