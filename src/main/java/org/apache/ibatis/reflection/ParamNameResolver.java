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
package org.apache.ibatis.reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

//todo 可以用来处理mapper接口中的方法的参数列表
public class ParamNameResolver {

  public static final String GENERIC_NAME_PREFIX = "param";

  /**
   * <p>
   * The key is the index and the value is the name of the parameter.<br />
   * The name is obtained from {@link Param} if specified. When {@link Param} is not specified,
   * the parameter index is used. Note that this index could be different from the actual index
   * when the method has special parameters (i.e. {@link RowBounds} or {@link ResultHandler}).
   * </p>
   * <ul>
   * <li>aMethod(@Param("M") int a, @Param("N") int b) -&gt; {{0, "M"}, {1, "N"}}</li>
   * <li>aMethod(int a, int b) -&gt; {{0, "0"}, {1, "1"}}</li>
   * <li>aMethod(int a, RowBounds rb, int b) -&gt; {{0, "0"}, {2, "1"}}</li>
   * </ul>
   * todo 使用该字段记录了参数在参数列表中的位置索引与参数名称之间的对应关系
   *  key表示参数在参数列表中的索引位置
   *  value表示参数名称，参数名称可以通过@Param注解指定，如果没有指定@Param名称，则使用参数索引作为其名称
   *  如果参数列表中包含RowBounds类型活ResultHandler类型的参数，则这两种类型的参数是并不会被记录到name集合中的
   */
  private final SortedMap<Integer, String> names;

  //todo 记录了对应方法的参数列表中是否使用了@Param注解
  private boolean hasParamAnnotation;

  //todo 会通过反射的方式读取Mapper接口中对应方法的信息，并初始化 name 和 hasParamAnnotation字段
  public ParamNameResolver(Configuration config, Method method) {
    //todo 获取参数列表中各个参数的类型
    final Class<?>[] paramTypes = method.getParameterTypes();
    //todo 获取参数列表上的注解
    final Annotation[][] paramAnnotations = method.getParameterAnnotations();
    //todo 该集合用于记录参数索引与参数名称的对应关系
    final SortedMap<Integer, String> map = new TreeMap<>();
    int paramCount = paramAnnotations.length;
    // todo 遍历方法所有参数
    for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
      if (isSpecialParameter(paramTypes[paramIndex])) {
        // todo 如果参数是RowBounds类型或 ResultHandler类型，则跳过对该参数的分析
        continue;
      }
      String name = null;
      //todo 遍历该参数对应的注解集合
      for (Annotation annotation : paramAnnotations[paramIndex]) {
        if (annotation instanceof Param) {
          //todo 如果@Param注解出现过一次，就将hasParamAnnotation设置为true
          hasParamAnnotation = true;
          //todo 获取@Param注解指定的参数名称
          name = ((Param) annotation).value();
          break;
        }
      }
      if (name == null) {
        // todo 该参数没有对应的@Param注解，则根据配置决定是否使用参数实际名称作为其名称
        if (config.isUseActualParamName()) {
          name = getActualParamName(method, paramIndex);
        }
        if (name == null) {
          // use the parameter index as the name ("0", "1", ...)
          // gcode issue #71
          //todo 使用参数索引作为其名称
          name = String.valueOf(map.size());
        }
      }
      map.put(paramIndex, name);
    }
    //todo 初始化names集合
    names = Collections.unmodifiableSortedMap(map);
  }

  private String getActualParamName(Method method, int paramIndex) {
    return ParamNameUtil.getParamNames(method).get(paramIndex);
  }

  //todo 是用来过滤掉参数类型是RowBounds 和 ResultHandler的参数
  private static boolean isSpecialParameter(Class<?> clazz) {
    return RowBounds.class.isAssignableFrom(clazz) || ResultHandler.class.isAssignableFrom(clazz);
  }

  /**
   * Returns parameter names referenced by SQL providers.
   * todo
   */
  public String[] getNames() {
    return names.values().toArray(new String[0]);
  }

  /**
   * <p>
   * A single non-special parameter is returned without a name.
   * Multiple parameters are named using the naming rule.
   * In addition to the default names, this method also adds the generic names (param1, param2,
   * ...).
   * </p>
   * todo args 用户传入的实参列表，并将实参与其对应名称进行关联
   */
  public Object getNamedParams(Object[] args) {
    final int paramCount = names.size();
    //todo 无参数，返回null
    if (args == null || paramCount == 0) {
      return null;
    } else if (!hasParamAnnotation && paramCount == 1) {
      //todo 未使用@Param且只有一个参数
      return args[names.firstKey()];
    } else {
      //todo 处理使用@Param注解制定了参数名称或多个参数的情况
      // param这个map中记录了参数名称与实参之间的对应关系
      final Map<String, Object> param = new ParamMap<>();
      int i = 0;
      for (Map.Entry<Integer, String> entry : names.entrySet()) {
        //todo 将参数名与实参对应关系记录到param中
        param.put(entry.getValue(), args[entry.getKey()]);
        // add generic param names (param1, param2, ...)
        final String genericParamName = GENERIC_NAME_PREFIX + (i + 1);
        // ensure not to overwrite parameter named with @Param
        if (!names.containsValue(genericParamName)) {
          param.put(genericParamName, args[entry.getKey()]);
        }
        i++;
      }
      return param;
    }
  }
}
