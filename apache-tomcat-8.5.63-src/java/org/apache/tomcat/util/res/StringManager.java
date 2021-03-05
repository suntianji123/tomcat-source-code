/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.util.res;

import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * 字符串管理器
 */
public class StringManager {

    /**
     * 单个包名 对应的语言|字符串管理器表大小
     */
    private static int LOCALE_CACHE_SIZE = 10;

    /**
     * 资源边界对象
     */
    private final ResourceBundle bundle;

    /**
     * 语言类型
     */
    private final Locale locale;


    /**
     * 实例化一个字符串管理器
     * @param packageName 字符串管理器所管理的包名
     * @param locale 字符串管理器语言类型
     */
    private StringManager(String packageName, Locale locale) {
        //拼接包名
        String bundleName = packageName + ".LocalStrings";
        //资源边界对象
        ResourceBundle bnd = null;
        try {
            //使用english语言
            if (locale.getLanguage().equals(Locale.ENGLISH.getLanguage())) {
                //设置语言为root
                locale = Locale.ROOT;
            }
            //获取资源边界对象
            bnd = ResourceBundle.getBundle(bundleName, locale);
        } catch (MissingResourceException ex) {
            // Try from the current loader (that's the case for trusted apps)
            // Should only be required if using a TC5 style classloader structure
            // where common != shared != server
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl != null) {
                try {
                    bnd = ResourceBundle.getBundle(bundleName, locale, cl);
                } catch (MissingResourceException ex2) {
                    // Ignore
                }
            }
        }

        //设置资源边界对象
        bundle = bnd;
        // Get the actual locale, which may be different from the requested one
        if (bundle != null) {
            //获取本地语言对象
            Locale bundleLocale = bundle.getLocale();
            if (bundleLocale.equals(Locale.ROOT)) {
                this.locale = Locale.ENGLISH;
            } else {
                this.locale = bundleLocale;
            }
        } else {
            this.locale = null;
        }
    }


    /**
     * Get a string from the underlying resource bundle or return null if the
     * String is not found.
     *
     * @param key to desired resource String
     *
     * @return resource String matching <i>key</i> from underlying bundle or
     *         null if not found.
     *
     * @throws IllegalArgumentException if <i>key</i> is null
     */
    public String getString(String key) {
        if (key == null){
            String msg = "key may not have a null value";
            throw new IllegalArgumentException(msg);
        }

        String str = null;

        try {
            // Avoid NPE if bundle is null and treat it like an MRE
            if (bundle != null) {
                str = bundle.getString(key);
            }
        } catch (MissingResourceException mre) {
            //bad: shouldn't mask an exception the following way:
            //   str = "[cannot find message associated with key '" + key +
            //         "' due to " + mre + "]";
            //     because it hides the fact that the String was missing
            //     from the calling code.
            //good: could just throw the exception (or wrap it in another)
            //      but that would probably cause much havoc on existing
            //      code.
            //better: consistent with container pattern to
            //      simply return null.  Calling code can then do
            //      a null check.
            str = null;
        }

        return str;
    }


    /**
     * 获取某个属性值
     * @param key 属性
     * @param args 值参数
     * @return
     */
    public String getString(final String key, final Object... args) {
        //获取属性值
        String value = getString(key);
        if (value == null) {//属性值不存在 将属性key作为属性值
            value = key;
        }

        //消息格式化
        MessageFormat mf = new MessageFormat(value);
        //设置消息格式化的语言对象
        mf.setLocale(locale);
        //格式化属性值
        return mf.format(args, new StringBuffer(), null).toString();
    }


    /**
     * Identify the Locale this StringManager is associated with.
     *
     * @return The Locale associated with the StringManager
     */
    public Locale getLocale() {
        return locale;
    }

    /**
     * 包下对应的语言与字符串管理器map
     */
    private static final Map<String, Map<Locale,StringManager>> managers =
            new Hashtable<>();


    /**
     * 获取字符串资源管理器
     * @param clazz
     * @return
     */
    public static final StringManager getManager(Class<?> clazz) {
        return getManager(clazz.getPackage().getName());
    }


    /**
     * 获取某个包对应的字符串管理器
     * @param packageName 包名
     * @return
     */
    public static final StringManager getManager(String packageName) {
        return getManager(packageName, Locale.getDefault());
    }


    /**
     * 获取某个包对应的字符串管理器
     * @param packageName 包名
     * @param locale 本地语言对象
     * @return
     */
    public static final synchronized StringManager getManager(
            String packageName, Locale locale) {

        //从缓存中 获取报名对应的语言与管理器表
        Map<Locale,StringManager> map = managers.get(packageName);
        if (map == null) {//表不存在
            //实例化一个表
            map = new LinkedHashMap<Locale,StringManager>(LOCALE_CACHE_SIZE, 1, true) {
                private static final long serialVersionUID = 1L;
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<Locale,StringManager> eldest) {
                    if (size() > (LOCALE_CACHE_SIZE - 1)) {
                        return true;
                    }
                    return false;
                }
            };

            //向表中放入包 对应的表
            managers.put(packageName, map);
        }

        //从表中获取语言对应的字符串管理器
        StringManager mgr = map.get(locale);
        if (mgr == null) {//字符串管理器不存在
            //实例化一个字符串管理器
            mgr = new StringManager(packageName, locale);
            //将字符串管理器放入表中
            map.put(locale, mgr);
        }
        //返回字符串管理器
        return mgr;
    }


    /**
     * Retrieve the StringManager for a list of Locales. The first StringManager
     * found will be returned.
     *
     * @param packageName      The package for which the StringManager was
     *                         requested
     * @param requestedLocales The list of Locales
     *
     * @return the found StringManager or the default StringManager
     */
    public static StringManager getManager(String packageName,
            Enumeration<Locale> requestedLocales) {
        while (requestedLocales.hasMoreElements()) {
            Locale locale = requestedLocales.nextElement();
            StringManager result = getManager(packageName, locale);
            if (result.getLocale().equals(locale)) {
                return result;
            }
        }
        // Return the default
        return getManager(packageName);
    }
}
