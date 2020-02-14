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
package org.apache.ibatis.plugin;

import java.util.Properties;

/**
 * @author Clinton Begin
 * todo  插件模块
 */
public interface Interceptor {

  //todo 执行拦截逻辑的方法，执行最后 调用invocation执行目标方法
  Object intercept(Invocation invocation) throws Throwable;

  //todo 决定是否触发intercept方法
  default Object plugin(Object target) {
    //todo 用户自定义的pluin()方法，可以使用mybatis提供的Pluin工具类实现
    return Plugin.wrap(target, this);
  }

  //todo 根据配置初始化interceptor对象
  default void setProperties(Properties properties) {
    // NOP
  }

}
