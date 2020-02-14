/**
 *    Copyright 2009-2016 the original author or authors.
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
package org.apache.ibatis.parsing;

import java.util.Properties;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 * todo 用于解析配置信息文件 Properties
 */
public class PropertyParser {

  private static final String KEY_PREFIX = "org.apache.ibatis.parsing.PropertyParser.";
  /**
   * The special property key that indicate whether enable a default value on placeholder.
   * <p>
   *   The default value is {@code false} (indicate disable a default value on placeholder)
   *   If you specify the {@code true}, you can specify key and default value on placeholder (e.g. {@code ${db.username:postgres}}).
   * </p>
   * @since 3.4.2
   * todo  在mybatis-config.xml中<properties>节点下配置是否开启默认值功能对应配置项
   */
  public static final String KEY_ENABLE_DEFAULT_VALUE = KEY_PREFIX + "enable-default-value";

  /**
   * The special property key that specify a separator for key and default value on placeholder.
   * <p>
   *   The default separator is {@code ":"}.
   * </p>
   * @since 3.4.2
   * todo 配置占位符与默认值之间的默认分隔符的对应配置项
   */
  public static final String KEY_DEFAULT_VALUE_SEPARATOR = KEY_PREFIX + "default-value-separator";

  //todo 默认情况下，关闭默认值的功能
  private static final String ENABLE_DEFAULT_VALUE = "false";
  //todo 默认分隔符为 冒号：
  private static final String DEFAULT_VALUE_SEPARATOR = ":";

  private PropertyParser() {
    // Prevent Instantiation
  }

  public static String parse(String string, Properties variables) {
    VariableTokenHandler handler = new VariableTokenHandler(variables);
    //todo 创建 GenericTokenParser，并将解析默认值的处理委托给他
    GenericTokenParser parser = new GenericTokenParser("${", "}", handler);
    return parser.parse(string);
  }

  private static class VariableTokenHandler implements TokenHandler {
    //todo <properties> 节点下定义的键值对，用于替换占位符
    private final Properties variables;
    //todo 是否支持占位符中使用默认值的功能
    private final boolean enableDefaultValue;
    //todo 指定占位符和默认值之间的分隔符
    private final String defaultValueSeparator;

    private VariableTokenHandler(Properties variables) {
      this.variables = variables;
      this.enableDefaultValue = Boolean.parseBoolean(getPropertyValue(KEY_ENABLE_DEFAULT_VALUE, ENABLE_DEFAULT_VALUE));
      this.defaultValueSeparator = getPropertyValue(KEY_DEFAULT_VALUE_SEPARATOR, DEFAULT_VALUE_SEPARATOR);
    }

    private String getPropertyValue(String key, String defaultValue) {
      return (variables == null) ? defaultValue : variables.getProperty(key, defaultValue);
    }
    //todo 该方法实现首先会按照 defaultValueSeparator字段指定的分隔符对整个占位符切分，得到占位符的名称与默认值
    // 如果在<properties> 节点下未定义相应的键值对，则将切分得到的默认值作为解析结果返回
    // 举例 有一个数据库用户名为 ${username:root} :是占位符和默认值的分隔符，如果在Properties中找不到就使用默认值root
    @Override
    public String handleToken(String content) {
      //todo 检测variables集合是否为空
      if (variables != null) {
        String key = content;
        //todo 检测是否支持占位符中使用默认值的功能
        if (enableDefaultValue) {
          //todo 查找分隔符
          final int separatorIndex = content.indexOf(defaultValueSeparator);
          String defaultValue = null;
          if (separatorIndex >= 0) {
            //todo 获取占位符的名称 username
            key = content.substring(0, separatorIndex);
            //todo 获取默认值 root
            defaultValue = content.substring(separatorIndex + defaultValueSeparator.length());
          }
          if (defaultValue != null) {
            //todo 如果properties 中没定义值，就返回他自己配置的默认值
            return variables.getProperty(key, defaultValue);
          }
        }
        //todo 不支持默认值的功能的话，直接从properties中查找
        if (variables.containsKey(key)) {
          return variables.getProperty(key);
        }
      }
      //todo 如果没有找到默认值，则返回这个格式的数据
      return "${" + content + "}";
    }
  }

}
