/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.tomcat.util.digester;


import org.xml.sax.Attributes;


/**
 * Rule implementation that creates a new object and pushes it
 * onto the object stack.  When the element is complete, the
 * object will be popped
 */

public class ObjectCreateRule extends Rule {


    // ----------------------------------------------------------- Constructors


    /**
     * Construct an object create rule with the specified class name.
     *
     * @param className Java class name of the object to be created
     */
    public ObjectCreateRule(String className) {

        this(className, (String) null);

    }


    /**
     * 实例化一个对象创建规则对象
     * @param className 对象的class类的全类名 org.apache.catalina.core.StandardServer
     * @param attributeName 属性名
     */
    public ObjectCreateRule(String className,
                            String attributeName) {

        //设置className
        this.className = className;
        //设置属性名
        this.attributeName = attributeName;

    }


    // ----------------------------------------------------- Instance Variables

    /**
     * 自定义的属性名
     */
    protected String attributeName = null;


    /**
     * 对象的class的全类名
     */
    protected String className = null;


    // --------------------------------------------------------- Public Methods


    /**
     *
     * @param namespace 匹配规则的命名空间
     *                  empty string if the parser is not namespace aware or the
     *                  element has no namespace
     * @param name 标签名
     *             the element name otherwise
     * @param attributes 标签的属性列表
     *
     * @throws Exception
     */
    @Override
    public void begin(String namespace, String name, Attributes attributes)
            throws Exception {

        //创建对象的class全路径
        String realClassName = className;
        if (attributeName != null) {//自定义的属性名
            //获取自定义属性名的值
            String value = attributes.getValue(attributeName);
            if (value != null) {//指定了自定义属性名 className属性 使用其他类来创建对象
                realClassName = value;
            }
        }
        if (digester.log.isDebugEnabled()) {
            digester.log.debug("[ObjectCreateRule]{" + digester.match +
                    "}New " + realClassName);
        }

        if (realClassName == null) {
            throw new NullPointerException("No class name specified for " +
                    namespace + " " + name);
        }

       //获取加载器  将class类加载到虚拟机
        Class<?> clazz = digester.getClassLoader().loadClass(realClassName);
        //构造实例
        Object instance = clazz.getConstructor().newInstance();
        //将创建的实例添加到对象栈
        digester.push(instance);
    }


    /**
     * Process the end of this element.
     *
     * @param namespace the namespace URI of the matching element, or an
     *   empty string if the parser is not namespace aware or the element has
     *   no namespace
     * @param name the local name if the parser is namespace aware, or just
     *   the element name otherwise
     */
    @Override
    public void end(String namespace, String name) throws Exception {

        Object top = digester.pop();
        if (digester.log.isDebugEnabled()) {
            digester.log.debug("[ObjectCreateRule]{" + digester.match +
                    "} Pop " + top.getClass().getName());
        }

    }


    /**
     * Render a printable version of this Rule.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ObjectCreateRule[");
        sb.append("className=");
        sb.append(className);
        sb.append(", attributeName=");
        sb.append(attributeName);
        sb.append(']');
        return sb.toString();
    }


}
