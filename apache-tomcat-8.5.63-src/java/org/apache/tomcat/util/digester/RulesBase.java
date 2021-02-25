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


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


/**
 * <p>Default implementation of the <code>Rules</code> interface that supports
 * the standard rule matching behavior.  This class can also be used as a
 * base class for specialized <code>Rules</code> implementations.</p>
 *
 * <p>The matching policies implemented by this class support two different
 * types of pattern matching rules:</p>
 * <ul>
 * <li><em>Exact Match</em> - A pattern "a/b/c" exactly matches a
 *     <code>&lt;c&gt;</code> element, nested inside a <code>&lt;b&gt;</code>
 *     element, which is nested inside an <code>&lt;a&gt;</code> element.</li>
 * <li><em>Tail Match</em> - A pattern "&#42;/a/b" matches a
 *     <code>&lt;b&gt;</code> element, nested inside an <code>&lt;a&gt;</code>
 *      element, no matter how deeply the pair is nested.</li>
 * </ul>
 */

public class RulesBase implements Rules {


    // ----------------------------------------------------- Instance Variables


    /**
     * 匹配对应的规则列表 {"Server":[ObjectCreateRule,SetPropertiesRule,SetNextRule]}
     */
    protected HashMap<String,List<Rule>> cache = new HashMap<>();


    /**
     * The Digester instance with which this Rules instance is associated.
     */
    protected Digester digester = null;


    /**
     * The namespace URI for which subsequently added <code>Rule</code>
     * objects are relevant, or <code>null</code> for matching independent
     * of namespaces.
     *
     * @deprecated Unused. Will be removed in Tomcat 9.0.x
     */
    @Deprecated
    protected String namespaceURI = null;


    /**
     * 规则列表  [ObjectCreateRule,SetPropertiesRule,SetNextRule]
     */
    protected ArrayList<Rule> rules = new ArrayList<>();


    // ------------------------------------------------------------- Properties


    /**
     * Return the Digester instance with which this Rules instance is
     * associated.
     */
    @Override
    public Digester getDigester() {
        return this.digester;
    }


    /**
     * Set the Digester instance with which this Rules instance is associated.
     *
     * @param digester The newly associated Digester instance
     */
    @Override
    public void setDigester(Digester digester) {

        this.digester = digester;
        for (Rule item : rules) {
            item.setDigester(digester);
        }

    }


    /**
     * Return the namespace URI that will be applied to all subsequently
     * added <code>Rule</code> objects.
     */
    @Override
    public String getNamespaceURI() {
        return this.namespaceURI;
    }


    /**
     * Set the namespace URI that will be applied to all subsequently
     * added <code>Rule</code> objects.
     *
     * @param namespaceURI Namespace URI that must match on all
     *  subsequently added rules, or <code>null</code> for matching
     *  regardless of the current namespace URI
     */
    @Override
    public void setNamespaceURI(String namespaceURI) {

        this.namespaceURI = namespaceURI;

    }


    // --------------------------------------------------------- Public Methods


    /**
     * 添加一个规则
     * @param pattern 匹配正则
     * @param rule 规则对象
     */
    @Override
    public void add(String pattern, Rule rule) {
        //获取匹配正则的长度
        int patternLength = pattern.length();
        if (patternLength>1 && pattern.endsWith("/")) {//去掉结尾的/
            pattern = pattern.substring(0, patternLength-1);
        }

        //获取正则的规则列表
        List<Rule> list = cache.get(pattern);
        if (list == null) {//列表为null
            //实例化列表
            list = new ArrayList<>();
            //将列表放入map缓存对象
            cache.put(pattern, list);
        }

        //将当前规则添加到 匹配正则对应的规则列表
        list.add(rule);
        //添加一份到规则里诶包
        rules.add(rule);
        if (this.digester != null) {
            //规则持有digester对象的引用
            rule.setDigester(this.digester);
        }
        if (this.namespaceURI != null) {
            rule.setNamespaceURI(this.namespaceURI);
        }

    }


    /**
     * Clear all existing Rule instance registrations.
     */
    @Override
    public void clear() {

        cache.clear();
        rules.clear();

    }


    /**
     * 查找规则对应的匹配器列表
     * @param namespaceURI Namespace URI for which to select matching rules,
     *  or <code>null</code> to match regardless of namespace URI
     * @param pattern 匹配字符串
     * @return
     */
    @Override
    public List<Rule> match(String namespaceURI, String pattern) {

        // List rulesList = (List) this.cache.get(pattern);
        List<Rule> rulesList = lookup(namespaceURI, pattern);
        if ((rulesList == null) || (rulesList.size() < 1)) {
            // Find the longest key, ie more discriminant
            String longKey = "";
            for (String key : this.cache.keySet()) {
                if (key.startsWith("*/")) {
                    if (pattern.equals(key.substring(2)) ||
                        pattern.endsWith(key.substring(1))) {
                        if (key.length() > longKey.length()) {
                            // rulesList = (List) this.cache.get(key);
                            rulesList = lookup(namespaceURI, key);
                            longKey = key;
                        }
                    }
                }
            }
        }
        if (rulesList == null) {
            rulesList = new ArrayList<>();
        }
        return rulesList;
    }


    /**
     * Return a List of all registered Rule instances, or a zero-length List
     * if there are no registered Rule instances.  If more than one Rule
     * instance has been registered, they <strong>must</strong> be returned
     * in the order originally registered through the <code>add()</code>
     * method.
     */
    @Override
    public List<Rule> rules() {
        return this.rules;
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 查找某个匹配字符串对应的规则列表
     * @param namespaceURI 规则的命名空间
     * @param pattern 匹配字符串
     * @return
     */
    protected List<Rule> lookup(String namespaceURI, String pattern) {
        //获取匹配字符串对应的规则列表
        List<Rule> list = this.cache.get(pattern);
        if (list == null) {//列表为null 直接返回
            return null;
        }
        if ((namespaceURI == null) || (namespaceURI.length() == 0)) {
            return list;
        }

        //实例化list数组
        ArrayList<Rule> results = new ArrayList<>();
        for (Rule item : list) {//遍历规则列表 返回命名空间匹配的规则列表
            if ((namespaceURI.equals(item.getNamespaceURI())) ||
                    (item.getNamespaceURI() == null)) {
                results.add(item);
            }
        }
        return results;
    }


}
