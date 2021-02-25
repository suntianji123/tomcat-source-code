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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EmptyStackException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.PropertyPermission;
import java.util.Set;
import java.util.StringTokenizer;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.IntrospectionUtils.PropertySource;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.PermissionCheck;
import org.xml.sax.Attributes;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;
import org.xml.sax.ext.EntityResolver2;
import org.xml.sax.ext.Locator2;
import org.xml.sax.helpers.AttributesImpl;


/**
 * 用于启动Calatinal的对象类
 */
public class Digester extends DefaultHandler2 {

    // ---------------------------------------------------------- Static Fields

    protected static IntrospectionUtils.PropertySource[] propertySources;
    private static boolean propertySourcesSet = false;
    protected static final StringManager sm = StringManager.getManager(Digester.class);

    static {
        String classNames = System.getProperty("org.apache.tomcat.util.digester.PROPERTY_SOURCE");
        ArrayList<IntrospectionUtils.PropertySource> sourcesList = new ArrayList<>();
        IntrospectionUtils.PropertySource[] sources = null;
        if (classNames != null) {
            StringTokenizer classNamesTokenizer = new StringTokenizer(classNames, ",");
            while (classNamesTokenizer.hasMoreTokens()) {
                String className = classNamesTokenizer.nextToken().trim();
                ClassLoader[] cls = new ClassLoader[] { Digester.class.getClassLoader(),
                        Thread.currentThread().getContextClassLoader() };
                for (ClassLoader cl : cls) {
                    try {
                        Class<?> clazz = Class.forName(className, true, cl);
                        sourcesList.add((PropertySource) clazz.getConstructor().newInstance());
                        break;
                    } catch (Throwable t) {
                        ExceptionUtils.handleThrowable(t);
                    LogFactory.getLog("org.apache.tomcat.util.digester.Digester")
                            .error("Unable to load property source[" + className + "].", t);
                    }
                }
            }
            sources = sourcesList.toArray(new IntrospectionUtils.PropertySource[0]);
        }
        if (sources != null) {
            propertySources = sources;
            propertySourcesSet = true;
        }
        if (Boolean.getBoolean("org.apache.tomcat.util.digester.REPLACE_SYSTEM_PROPERTIES")) {
            replaceSystemProperties();
        }
    }

    public static void setPropertySource(IntrospectionUtils.PropertySource propertySource) {
        if (!propertySourcesSet) {
            propertySources = new IntrospectionUtils.PropertySource[1];
            propertySources[0] = propertySource;
            propertySourcesSet = true;
        }
    }

    public static void setPropertySource(IntrospectionUtils.PropertySource[] propertySources) {
        if (!propertySourcesSet) {
            Digester.propertySources = propertySources;
            propertySourcesSet = true;
        }
    }

    // --------------------------------------------------- Instance Variables


    private static class SystemPropertySource implements IntrospectionUtils.SecurePropertySource {

        @Override
        public String getProperty(String key) {
            // For backward compatibility
            return getProperty(key, null);
        }

        @Override
        public String getProperty(String key, ClassLoader classLoader) {
            if (classLoader instanceof PermissionCheck) {
                Permission p = new PropertyPermission(key, "read");
                if (!((PermissionCheck) classLoader).check(p)) {
                    return null;
                }
            }
            return System.getProperty(key);
        }
    }

    /**
     * A {@link org.apache.tomcat.util.IntrospectionUtils.SecurePropertySource}
     * that uses environment variables to resolve expressions. Still available
     * for backwards compatibility.
     *
     * @deprecated Use {@link org.apache.tomcat.util.digester.EnvironmentPropertySource}
     *             This will be removed in Tomcat 10 onwards.
     */
    @Deprecated
    public static class EnvironmentPropertySource extends org.apache.tomcat.util.digester.EnvironmentPropertySource {
    }


    protected IntrospectionUtils.PropertySource[] source = new IntrospectionUtils.PropertySource[] {
            new SystemPropertySource() };


    /**
     * The body text of the current element.
     */
    protected StringBuilder bodyText = new StringBuilder();


    /**
     * The stack of body text string buffers for surrounding elements.
     */
    protected ArrayStack<StringBuilder> bodyTexts = new ArrayStack<>();


    /**
     * 匹配的规则列表栈
     */
    protected ArrayStack<List<Rule>> matches = new ArrayStack<>(10);

    /**
     * The class loader to use for instantiating application objects.
     * If not specified, the context class loader, or the class loader
     * used to load Digester itself, is used, based on the value of the
     * <code>useContextClassLoader</code> variable.
     */
    protected ClassLoader classLoader = null;


    /**
     * Has this Digester been configured yet.
     */
    protected boolean configured = false;


    /**
     * The EntityResolver used by the SAX parser. By default it use this class
     */
    protected EntityResolver entityResolver;

    /**
     * The URLs of entityValidator that have been registered, keyed by the public
     * identifier that corresponds.
     */
    protected HashMap<String, String> entityValidator = new HashMap<>();


    /**
     * The application-supplied error handler that is notified when parsing
     * warnings, errors, or fatal errors occur.
     */
    protected ErrorHandler errorHandler = null;


    /**
     * SAXParser解析器工厂
     */
    protected SAXParserFactory factory = null;

    /**
     * The Locator associated with our parser.
     */
    protected Locator locator = null;


    /**
     * 匹配正则
     */
    protected String match = "";


    /**
     * Do we want a "namespace aware" parser.
     */
    protected boolean namespaceAware = false;


    /**
     * Registered namespaces we are currently processing.  The key is the
     * namespace prefix that was declared in the document.  The value is an
     * ArrayStack of the namespace URIs this prefix has been mapped to --
     * the top Stack element is the most current one.  (This architecture
     * is required because documents can declare nested uses of the same
     * prefix for different Namespace URIs).
     */
    protected HashMap<String, ArrayStack<String>> namespaces = new HashMap<>();


    /**
     * The parameters stack being utilized by CallMethodRule and
     * CallParamRule rules.
     */
    protected ArrayStack<Object> params = new ArrayStack<>();


    /**
     * The SAXParser we will use to parse the input stream.
     */
    protected SAXParser parser = null;


    /**
     * The public identifier of the DTD we are currently parsing under
     * (if any).
     */
    protected String publicId = null;


    /**
     * 用于读取.xml文件的xml阅读器对象
     */
    protected XMLReader reader = null;


    /**
     * The "root" element of the stack (in other words, the last object
     * that was popped.
     */
    protected Object root = null;


    /**
     * The <code>Rules</code> implementation containing our collection of
     * <code>Rule</code> instances and associated matching policy.  If not
     * established before the first rule is added, a default implementation
     * will be provided.
     */
    protected Rules rules = null;

    /**
     * 解析xml标签获取的对象栈
     */
    protected ArrayStack<Object> stack = new ArrayStack<>();


    /**
     * 是否使用上下文类加载器 默认为true
     */
    protected boolean useContextClassLoader = false;


    /**
     * Do we want to use a validating parser.
     */
    protected boolean validating = false;


    /**
     * Warn on missing attributes and elements.
     */
    protected boolean rulesValidation = false;


    /**
     * 伪造的属性map {object.class:["className"],StandardContext.class:["source"]}
     */
    protected Map<Class<?>, List<String>> fakeAttributes = null;


    /**
     * The Log to which most logging calls will be made.
     */
    protected Log log = LogFactory.getLog(Digester.class);

    /**
     * The Log to which all SAX event related logging calls will be made.
     */
    protected Log saxLog = LogFactory.getLog("org.apache.tomcat.util.digester.Digester.sax");


    public Digester() {
        propertySourcesSet = true;
        if (propertySources != null) {
            ArrayList<IntrospectionUtils.PropertySource> sourcesList = new ArrayList<>();
            sourcesList.addAll(Arrays.asList(propertySources));
            sourcesList.add(source[0]);
            source = sourcesList.toArray(new IntrospectionUtils.PropertySource[0]);
        }
    }


    public static void replaceSystemProperties() {
        Log log = LogFactory.getLog(Digester.class);
        if (propertySources != null) {
            Properties properties = System.getProperties();
            Set<String> names = properties.stringPropertyNames();
            for (String name : names) {
                String value = System.getProperty(name);
                if (value != null) {
                    try {
                        String newValue = IntrospectionUtils.replaceProperties(value, null, propertySources, null);
                        if (!value.equals(newValue)) {
                            System.setProperty(name, newValue);
                        }
                    } catch (Exception e) {
                        log.warn(sm.getString("digester.failedToUpdateSystemProperty", name, value), e);
                    }
                }
            }
        }
    }


    // ------------------------------------------------------------- Properties

    /**
     * Return the currently mapped namespace URI for the specified prefix,
     * if any; otherwise return <code>null</code>.  These mappings come and
     * go dynamically as the document is parsed.
     *
     * @param prefix Prefix to look up
     * @return the namespace URI
     */
    public String findNamespaceURI(String prefix) {
        ArrayStack<String> stack = namespaces.get(prefix);
        if (stack == null) {
            return null;
        }
        try {
            return stack.peek();
        } catch (EmptyStackException e) {
            return null;
        }
    }


    /**
     * 获取类加载器
     * @return
     */
    public ClassLoader getClassLoader() {
        if (this.classLoader != null) {//指定了类加载器 直接返回加载器
            return this.classLoader;
        }
        if (this.useContextClassLoader) {//如果没有指定类加载器  则使用当前线程的上下文类加载器
            //类加载器
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader != null) {//类加载器存在 直接返回加载器
                return classLoader;
            }
        }
        return this.getClass().getClassLoader();
    }


    /**
     * Set the class loader to be used for instantiating application objects
     * when required.
     *
     * @param classLoader The new class loader to use, or <code>null</code>
     *  to revert to the standard rules
     */
    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }


    /**
     * @return the current depth of the element stack.
     */
    public int getCount() {
        return stack.size();
    }


    /**
     * @return the name of the XML element that is currently being processed.
     */
    public String getCurrentElementName() {
        String elementName = match;
        int lastSlash = elementName.lastIndexOf('/');
        if (lastSlash >= 0) {
            elementName = elementName.substring(lastSlash + 1);
        }
        return elementName;
    }


    /**
     * @return the error handler for this Digester.
     */
    public ErrorHandler getErrorHandler() {
        return this.errorHandler;
    }


    /**
     * Set the error handler for this Digester.
     *
     * @param errorHandler The new error handler
     */
    public void setErrorHandler(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }


    /**
     * 获取SAXParser 解析器工厂
     * @return
     * @throws SAXNotRecognizedException
     * @throws SAXNotSupportedException
     * @throws ParserConfigurationException
     */
    public SAXParserFactory getFactory() throws SAXNotRecognizedException, SAXNotSupportedException,
            ParserConfigurationException {

        if (factory == null) {//解析器为null  实例化一个SAXFactory对象
            factory = SAXParserFactory.newInstance();

            //设置工厂不适用命名空间
            factory.setNamespaceAware(namespaceAware);
            // Preserve xmlns attributes
            if (namespaceAware) {//如果使用命名空间
                //设置工厂商标
                factory.setFeature("http://xml.org/sax/features/namespace-prefixes", true);
            }

            //设置不校验
            factory.setValidating(validating);
            if (validating) {
                // Enable DTD validation
                factory.setFeature("http://xml.org/sax/features/validation", true);
                // Enable schema validation
                factory.setFeature("http://apache.org/xml/features/validation/schema", true);
            }
        }
        return factory;
    }


    /**
     * Sets a flag indicating whether the requested feature is supported
     * by the underlying implementation of <code>org.xml.sax.XMLReader</code>.
     * See <a href="http://www.saxproject.org/apidoc/xml/sax/package-summary.html#package-description">
     * http://www.saxproject.org/apidoc/xml/sax/package-summary.html#package-description</a>
     * for information about the standard SAX2 feature flags.  In order to be
     * effective, this method must be called <strong>before</strong> the
     * <code>getParser()</code> method is called for the first time, either
     * directly or indirectly.
     *
     * @param feature Name of the feature to set the status for
     * @param value The new value for this feature
     *
     * @exception ParserConfigurationException if a parser configuration error
     *  occurs
     * @exception SAXNotRecognizedException if the property name is
     *  not recognized
     * @exception SAXNotSupportedException if the property name is
     *  recognized but not supported
     */
    public void setFeature(String feature, boolean value) throws ParserConfigurationException,
            SAXNotRecognizedException, SAXNotSupportedException {

        getFactory().setFeature(feature, value);

    }


    /**
     * @return the current Logger associated with this instance of the Digester
     */
    public Log getLogger() {

        return log;

    }


    /**
     * Set the current logger for this Digester.
     * @param log The logger that will be used
     */
    public void setLogger(Log log) {

        this.log = log;

    }

    /**
     * Gets the logger used for logging SAX-related information.
     * <strong>Note</strong> the output is finely grained.
     *
     * @since 1.6
     * @return the SAX logger
     */
    public Log getSAXLogger() {

        return saxLog;
    }


    /**
     * Sets the logger used for logging SAX-related information.
     * <strong>Note</strong> the output is finely grained.
     * @param saxLog Log, not null
     *
     * @since 1.6
     */
    public void setSAXLogger(Log saxLog) {

        this.saxLog = saxLog;
    }

    /**
     * @return the current rule match path
     */
    public String getMatch() {

        return match;

    }


    /**
     * @return the "namespace aware" flag for parsers we create.
     */
    public boolean getNamespaceAware() {
        return this.namespaceAware;
    }


    /**
     * Set the "namespace aware" flag for parsers we create.
     *
     * @param namespaceAware The new "namespace aware" flag
     */
    public void setNamespaceAware(boolean namespaceAware) {
        this.namespaceAware = namespaceAware;
    }


    /**
     * Set the public id of the current file being parse.
     * @param publicId the DTD/Schema public's id.
     */
    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }


    /**
     * @return the public identifier of the DTD we are currently
     * parsing under, if any.
     */
    public String getPublicId() {
        return this.publicId;
    }


    /**
     * @return the namespace URI that will be applied to all subsequently
     * added <code>Rule</code> objects.
     *
     * @deprecated Unused. Will be removed in Tomcat 9
     */
    @Deprecated
    public String getRuleNamespaceURI() {
        return getRules().getNamespaceURI();
    }


    /**
     * Set the namespace URI that will be applied to all subsequently
     * added <code>Rule</code> objects.
     *
     * @param ruleNamespaceURI Namespace URI that must match on all
     *  subsequently added rules, or <code>null</code> for matching
     *  regardless of the current namespace URI
     *
     * @deprecated Unused. Will be removed in Tomcat 9
     */
    @Deprecated
    public void setRuleNamespaceURI(String ruleNamespaceURI) {
        getRules().setNamespaceURI(ruleNamespaceURI);
    }


    /**
     * 获取解析器
     * @return
     */
    public SAXParser getParser() {

        // Return the parser we already created (if any)
        if (parser != null) {//解析器存在 直接返回
            return parser;
        }

        // Create a new parser
        try {

            //调用工厂生产一个SAXParset解析器
            parser = getFactory().newSAXParser();
        } catch (Exception e) {
            log.error("Digester.getParser: ", e);
            return null;
        }

        return parser;
    }


    /**
     * Return the current value of the specified property for the underlying
     * <code>XMLReader</code> implementation.
     * See <a href="http://www.saxproject.org/apidoc/xml/sax/package-summary.html#package-description">
     * http://www.saxproject.org/apidoc/xml/sax/package-summary.html#package-description</a>
     * for information about the standard SAX2 properties.
     *
     * @param property Property name to be retrieved
     * @return the property value
     * @exception SAXNotRecognizedException if the property name is
     *  not recognized
     * @exception SAXNotSupportedException if the property name is
     *  recognized but not supported
     */
    public Object getProperty(String property)
            throws SAXNotRecognizedException, SAXNotSupportedException {

        return getParser().getProperty(property);
    }


    /**
     * 获取规则对象
     * @return
     */
    public Rules getRules() {
        if (this.rules == null) {
            //基本规则
            this.rules = new RulesBase();
            this.rules.setDigester(this);
        }
        return this.rules;
    }


    /**
     * Set the <code>Rules</code> implementation object containing our
     * rules collection and associated matching policy.
     *
     * @param rules New Rules implementation
     */
    public void setRules(Rules rules) {
        this.rules = rules;
        this.rules.setDigester(this);
    }


    /**
     * @return the boolean as to whether the context classloader should be used.
     */
    public boolean getUseContextClassLoader() {
        return useContextClassLoader;
    }


    /**
     * Determine whether to use the Context ClassLoader (the one found by
     * calling <code>Thread.currentThread().getContextClassLoader()</code>)
     * to resolve/load classes that are defined in various rules.  If not
     * using Context ClassLoader, then the class-loading defaults to
     * using the calling-class' ClassLoader.
     *
     * @param use determines whether to use Context ClassLoader.
     */
    public void setUseContextClassLoader(boolean use) {

        useContextClassLoader = use;

    }


    /**
     * @return the validating parser flag.
     */
    public boolean getValidating() {
        return this.validating;
    }


    /**
     * Set the validating parser flag.  This must be called before
     * <code>parse()</code> is called the first time.
     *
     * @param validating The new validating parser flag.
     */
    public void setValidating(boolean validating) {
        this.validating = validating;
    }


    /**
     * @return the rules validation flag.
     */
    public boolean getRulesValidation() {
        return this.rulesValidation;
    }


    /**
     * Set the rules validation flag.  This must be called before
     * <code>parse()</code> is called the first time.
     *
     * @param rulesValidation The new rules validation flag.
     */
    public void setRulesValidation(boolean rulesValidation) {
        this.rulesValidation = rulesValidation;
    }


    /**
     * @return the fake attributes list.
     */
    public Map<Class<?>, List<String>> getFakeAttributes() {
        return this.fakeAttributes;
    }


    /**
     * Determine if an attribute is a fake attribute.
     * @param object The object
     * @param name The attribute name
     * @return <code>true</code> if this is a fake attribute
     */
    public boolean isFakeAttribute(Object object, String name) {
        if (fakeAttributes == null) {
            return false;
        }
        List<String> result = fakeAttributes.get(object.getClass());
        if (result == null) {
            result = fakeAttributes.get(Object.class);
        }
        if (result == null) {
            return false;
        } else {
            return result.contains(name);
        }
    }


    /**
     * Set the fake attributes.
     *
     * @param fakeAttributes The new fake attributes.
     */
    public void setFakeAttributes(Map<Class<?>, List<String>> fakeAttributes) {

        this.fakeAttributes = fakeAttributes;

    }


    /**
     * 获取xml阅读器对象
     * @return
     * @throws SAXException
     */
    public XMLReader getXMLReader() throws SAXException {
        if (reader == null) {//当前xml解析器为null
            //设置xml阅读器为SAXFactory工厂生产的阅读器
            reader = getParser().getXMLReader();
        }

        //设置阅读的dtdhandler
        reader.setDTDHandler(this);
        //社会内容处理器
        reader.setContentHandler(this);

        //实体解析器
        EntityResolver entityResolver = getEntityResolver();
        if (entityResolver == null) {
            //实体解析器为当前对象
            entityResolver = this;
        }

        // Wrap the resolver so we can perform ${...} property replacement
        if (entityResolver instanceof EntityResolver2) {//包装试题解析器2
            entityResolver = new EntityResolver2Wrapper((EntityResolver2) entityResolver, source, classLoader);
        } else {
            entityResolver = new EntityResolverWrapper(entityResolver, source, classLoader);
        }

        //设置实体解析器
        reader.setEntityResolver(entityResolver);

        //设置阅读器的属性
        reader.setProperty("http://xml.org/sax/properties/lexical-handler", this);

        //设置解析错误的handler
        reader.setErrorHandler(this);

        //返回阅读器对象
        return reader;
    }

    // ------------------------------------------------- ContentHandler Methods


    /**
     * Process notification of character data received from the body of
     * an XML element.
     *
     * @param buffer The characters from the XML document
     * @param start Starting offset into the buffer
     * @param length Number of characters from the buffer
     *
     * @exception SAXException if a parsing error is to be reported
     */
    @Override
    public void characters(char buffer[], int start, int length) throws SAXException {

        if (saxLog.isDebugEnabled()) {
            saxLog.debug("characters(" + new String(buffer, start, length) + ")");
        }

        bodyText.append(buffer, start, length);

    }


    /**
     * Process notification of the end of the document being reached.
     *
     * @exception SAXException if a parsing error is to be reported
     */
    @Override
    public void endDocument() throws SAXException {

        if (saxLog.isDebugEnabled()) {
            if (getCount() > 1) {
                saxLog.debug("endDocument():  " + getCount() + " elements left");
            } else {
                saxLog.debug("endDocument()");
            }
        }

        while (getCount() > 1) {
            pop();
        }

        // Fire "finish" events for all defined rules
        for (Rule rule : getRules().rules()) {
            try {
                rule.finish();
            } catch (Exception e) {
                log.error("Finish event threw exception", e);
                throw createSAXException(e);
            } catch (Error e) {
                log.error("Finish event threw error", e);
                throw e;
            }
        }

        // Perform final cleanup
        clear();

    }


    /**
     * Process notification of the end of an XML element being reached.
     *
     * @param namespaceURI - The Namespace URI, or the empty string if the
     *   element has no Namespace URI or if Namespace processing is not
     *   being performed.
     * @param localName - The local name (without prefix), or the empty
     *   string if Namespace processing is not being performed.
     * @param qName - The qualified XML 1.0 name (with prefix), or the
     *   empty string if qualified names are not available.
     * @exception SAXException if a parsing error is to be reported
     */
    @Override
    public void endElement(String namespaceURI, String localName, String qName)
            throws SAXException {

        boolean debug = log.isDebugEnabled();

        if (debug) {
            if (saxLog.isDebugEnabled()) {
                saxLog.debug("endElement(" + namespaceURI + "," + localName + "," + qName + ")");
            }
            log.debug("  match='" + match + "'");
            log.debug("  bodyText='" + bodyText + "'");
        }

        // Parse system properties
        bodyText = updateBodyText(bodyText);

        // the actual element name is either in localName or qName, depending
        // on whether the parser is namespace aware
        String name = localName;
        if ((name == null) || (name.length() < 1)) {
            name = qName;
        }

        // Fire "body" events for all relevant rules
        List<Rule> rules = matches.pop();
        if ((rules != null) && (rules.size() > 0)) {
            String bodyText = this.bodyText.toString().intern();
            for (Rule value : rules) {
                try {
                    Rule rule = value;
                    if (debug) {
                        log.debug("  Fire body() for " + rule);
                    }
                    rule.body(namespaceURI, name, bodyText);
                } catch (Exception e) {
                    log.error("Body event threw exception", e);
                    throw createSAXException(e);
                } catch (Error e) {
                    log.error("Body event threw error", e);
                    throw e;
                }
            }
        } else {
            if (debug) {
                log.debug("  No rules found matching '" + match + "'.");
            }
            if (rulesValidation) {
                log.warn("  No rules found matching '" + match + "'.");
            }
        }

        // Recover the body text from the surrounding element
        bodyText = bodyTexts.pop();

        // Fire "end" events for all relevant rules in reverse order
        if (rules != null) {
            for (int i = 0; i < rules.size(); i++) {
                int j = (rules.size() - i) - 1;
                try {
                    Rule rule = rules.get(j);
                    if (debug) {
                        log.debug("  Fire end() for " + rule);
                    }
                    rule.end(namespaceURI, name);
                } catch (Exception e) {
                    log.error("End event threw exception", e);
                    throw createSAXException(e);
                } catch (Error e) {
                    log.error("End event threw error", e);
                    throw e;
                }
            }
        }

        // Recover the previous match expression
        int slash = match.lastIndexOf('/');
        if (slash >= 0) {
            match = match.substring(0, slash);
        } else {
            match = "";
        }

    }


    /**
     * Process notification that a namespace prefix is going out of scope.
     *
     * @param prefix Prefix that is going out of scope
     *
     * @exception SAXException if a parsing error is to be reported
     */
    @Override
    public void endPrefixMapping(String prefix) throws SAXException {

        if (saxLog.isDebugEnabled()) {
            saxLog.debug("endPrefixMapping(" + prefix + ")");
        }

        // Deregister this prefix mapping
        ArrayStack<String> stack = namespaces.get(prefix);
        if (stack == null) {
            return;
        }
        try {
            stack.pop();
            if (stack.empty())
                namespaces.remove(prefix);
        } catch (EmptyStackException e) {
            throw createSAXException("endPrefixMapping popped too many times");
        }

    }


    /**
     * Process notification of ignorable whitespace received from the body of
     * an XML element.
     *
     * @param buffer The characters from the XML document
     * @param start Starting offset into the buffer
     * @param len Number of characters from the buffer
     *
     * @exception SAXException if a parsing error is to be reported
     */
    @Override
    public void ignorableWhitespace(char buffer[], int start, int len) throws SAXException {

        if (saxLog.isDebugEnabled()) {
            saxLog.debug("ignorableWhitespace(" + new String(buffer, start, len) + ")");
        }

        // No processing required

    }


    /**
     * Process notification of a processing instruction that was encountered.
     *
     * @param target The processing instruction target
     * @param data The processing instruction data (if any)
     *
     * @exception SAXException if a parsing error is to be reported
     */
    @Override
    public void processingInstruction(String target, String data) throws SAXException {

        if (saxLog.isDebugEnabled()) {
            saxLog.debug("processingInstruction('" + target + "','" + data + "')");
        }

        // No processing is required

    }


    /**
     * Gets the document locator associated with our parser.
     *
     * @return the Locator supplied by the document parser
     */
    public Locator getDocumentLocator() {

        return locator;

    }

    /**
     * Sets the document locator associated with our parser.
     *
     * @param locator The new locator
     */
    @Override
    public void setDocumentLocator(Locator locator) {

        if (saxLog.isDebugEnabled()) {
            saxLog.debug("setDocumentLocator(" + locator + ")");
        }

        this.locator = locator;

    }


    /**
     * Process notification of a skipped entity.
     *
     * @param name Name of the skipped entity
     *
     * @exception SAXException if a parsing error is to be reported
     */
    @Override
    public void skippedEntity(String name) throws SAXException {

        if (saxLog.isDebugEnabled()) {
            saxLog.debug("skippedEntity(" + name + ")");
        }

        // No processing required

    }


    /**
     * Process notification of the beginning of the document being reached.
     *
     * @exception SAXException if a parsing error is to be reported
     */
    @SuppressWarnings("deprecation")
    @Override
    public void startDocument() throws SAXException {

        if (saxLog.isDebugEnabled()) {
            saxLog.debug("startDocument()");
        }

        if (locator instanceof Locator2) {
            if (root instanceof DocumentProperties.Charset) {
                String enc = ((Locator2) locator).getEncoding();
                if (enc != null) {
                    try {
                        ((DocumentProperties.Charset) root).setCharset(B2CConverter.getCharset(enc));
                    } catch (UnsupportedEncodingException e) {
                        log.warn(sm.getString("disgester.encodingInvalid", enc), e);
                    }
                }
            } else if (root instanceof DocumentProperties.Encoding) {
                ((DocumentProperties.Encoding) root).setEncoding(((Locator2) locator).getEncoding());
            }
        }

        // ensure that the digester is properly configured, as
        // the digester could be used as a SAX ContentHandler
        // rather than via the parse() methods.
        configure();
    }


    /**
     * 开始属性标签
     * @param namespaceURI 命名空间
     * @param localName 本地名
     * @param qName 标签名
     * @param list  属性列表
     * @throws SAXException
     */
    @Override
    public void startElement(String namespaceURI, String localName, String qName, Attributes list)
            throws SAXException {
        boolean debug = log.isDebugEnabled();

        if (saxLog.isDebugEnabled()) {
            saxLog.debug("startElement(" + namespaceURI + "," + localName + "," + qName + ")");
        }

        // 更新属性列表
        list = updateAttributes(list);

        // 标签字符串列表
        bodyTexts.push(bodyText);
        //设置标签字符串对象
        bodyText = new StringBuilder();

        //本地名
        String name = localName;
        if ((name == null) || (name.length() < 1)) {
            //名字为标签名
            name = qName;
        }

        //拼接字符串
        StringBuilder sb = new StringBuilder(match);
        if (match.length() > 0) {
            sb.append('/');
        }

        //sb：标签名：Server
        sb.append(name);

        //match ： Server
        match = sb.toString();
        if (debug) {
            log.debug("  New match='" + match + "'");
        }

        // 查找匹配器对应的规则列表
        List<Rule> rules = getRules().match(namespaceURI, match);
        //匹配的规则列表
        matches.push(rules);
        if ((rules != null) && (rules.size() > 0)) {
            for (Rule value : rules) {//遍历规则对象
                try {
                    //规则
                    Rule rule = value;
                    if (debug) {
                        log.debug("  Fire begin() for " + rule);
                    }

                    //调用规则的开始方法
                    rule.begin(namespaceURI, name, list);
                } catch (Exception e) {
                    log.error("Begin event threw exception", e);
                    throw createSAXException(e);
                } catch (Error e) {
                    log.error("Begin event threw error", e);
                    throw e;
                }
            }
        } else {
            if (debug) {
                log.debug("  No rules found matching '" + match + "'.");
            }
        }

    }


    /**
     * Process notification that a namespace prefix is coming in to scope.
     *
     * @param prefix Prefix that is being declared
     * @param namespaceURI Corresponding namespace URI being mapped to
     *
     * @exception SAXException if a parsing error is to be reported
     */
    @Override
    public void startPrefixMapping(String prefix, String namespaceURI) throws SAXException {

        if (saxLog.isDebugEnabled()) {
            saxLog.debug("startPrefixMapping(" + prefix + "," + namespaceURI + ")");
        }

        // Register this prefix mapping
        ArrayStack<String> stack = namespaces.get(prefix);
        if (stack == null) {
            stack = new ArrayStack<>();
            namespaces.put(prefix, stack);
        }
        stack.push(namespaceURI);

    }


    // ----------------------------------------------------- DTDHandler Methods


    /**
     * Receive notification of a notation declaration event.
     *
     * @param name The notation name
     * @param publicId The public identifier (if any)
     * @param systemId The system identifier (if any)
     */
    @Override
    public void notationDecl(String name, String publicId, String systemId) {

        if (saxLog.isDebugEnabled()) {
            saxLog.debug("notationDecl(" + name + "," + publicId + "," + systemId + ")");
        }

    }


    /**
     * Receive notification of an unparsed entity declaration event.
     *
     * @param name The unparsed entity name
     * @param publicId The public identifier (if any)
     * @param systemId The system identifier (if any)
     * @param notation The name of the associated notation
     */
    @Override
    public void unparsedEntityDecl(String name, String publicId, String systemId, String notation) {

        if (saxLog.isDebugEnabled()) {
            saxLog.debug("unparsedEntityDecl(" + name + "," + publicId + "," + systemId + ","
                    + notation + ")");
        }

    }


    // ----------------------------------------------- EntityResolver Methods

    /**
     * Set the <code>EntityResolver</code> used by SAX when resolving
     * public id and system id.
     * This must be called before the first call to <code>parse()</code>.
     * @param entityResolver a class that implement the <code>EntityResolver</code> interface.
     */
    public void setEntityResolver(EntityResolver entityResolver) {
        this.entityResolver = entityResolver;
    }


    /**
     * Return the Entity Resolver used by the SAX parser.
     * @return Return the Entity Resolver used by the SAX parser.
     */
    public EntityResolver getEntityResolver() {
        return entityResolver;
    }

    @Override
    public InputSource resolveEntity(String name, String publicId, String baseURI, String systemId)
            throws SAXException, IOException {

        if (saxLog.isDebugEnabled()) {
            saxLog.debug(
                    "resolveEntity('" + publicId + "', '" + systemId + "', '" + baseURI + "')");
        }

        // Has this system identifier been registered?
        String entityURL = null;
        if (publicId != null) {
            entityURL = entityValidator.get(publicId);
        }

        if (entityURL == null) {
            if (systemId == null) {
                // cannot resolve
                if (log.isDebugEnabled()) {
                    log.debug(" Cannot resolve entity: '" + publicId + "'");
                }
                return null;

            } else {
                // try to resolve using system ID
                if (log.isDebugEnabled()) {
                    log.debug(" Trying to resolve using system ID '" + systemId + "'");
                }
                entityURL = systemId;
                // resolve systemId against baseURI if it is not absolute
                if (baseURI != null) {
                    try {
                        URI uri = new URI(systemId);
                        if (!uri.isAbsolute()) {
                            entityURL = new URI(baseURI).resolve(uri).toString();
                        }
                    } catch (URISyntaxException e) {
                        if (log.isDebugEnabled()) {
                            log.debug("Invalid URI '" + baseURI + "' or '" + systemId + "'");
                        }
                    }
                }
            }
        }

        // Return an input source to our alternative URL
        if (log.isDebugEnabled()) {
            log.debug(" Resolving to alternate DTD '" + entityURL + "'");
        }

        try {
            return new InputSource(entityURL);
        } catch (Exception e) {
            throw createSAXException(e);
        }
    }


    // ----------------------------------------------- LexicalHandler Methods

    @Override
    public void startDTD(String name, String publicId, String systemId) throws SAXException {
        setPublicId(publicId);
    }


    // ------------------------------------------------- ErrorHandler Methods

    /**
     * Forward notification of a parsing error to the application supplied
     * error handler (if any).
     *
     * @param exception The error information
     *
     * @exception SAXException if a parsing exception occurs
     */
    @Override
    public void error(SAXParseException exception) throws SAXException {
        log.error("Parse Error at line " + exception.getLineNumber() + " column "
                + exception.getColumnNumber() + ": " + exception.getMessage(), exception);
        if (errorHandler != null) {
            errorHandler.error(exception);
        }
    }


    /**
     * Forward notification of a fatal parsing error to the application
     * supplied error handler (if any).
     *
     * @param exception The fatal error information
     *
     * @exception SAXException if a parsing exception occurs
     */
    @Override
    public void fatalError(SAXParseException exception) throws SAXException {
        log.error("Parse Fatal Error at line " + exception.getLineNumber() + " column "
                + exception.getColumnNumber() + ": " + exception.getMessage(), exception);
        if (errorHandler != null) {
            errorHandler.fatalError(exception);
        }
    }


    /**
     * Forward notification of a parse warning to the application supplied
     * error handler (if any).
     *
     * @param exception The warning information
     *
     * @exception SAXException if a parsing exception occurs
     */
    @Override
    public void warning(SAXParseException exception) throws SAXException {
        if (errorHandler != null) {
            log.warn(
                    "Parse Warning Error at line " + exception.getLineNumber() + " column "
                            + exception.getColumnNumber() + ": " + exception.getMessage(),
                    exception);

            errorHandler.warning(exception);
        }

    }


    // ------------------------------------------------------- Public Methods

    /**
     * Parse the content of the specified file using this Digester.  Returns
     * the root element from the object stack (if any).
     *
     * @param file File containing the XML data to be parsed
     * @return the root object
     * @exception IOException if an input/output error occurs
     * @exception SAXException if a parsing exception occurs
     */
    public Object parse(File file) throws IOException, SAXException {
        configure();
        InputSource input = new InputSource(new FileInputStream(file));
        input.setSystemId("file://" + file.getAbsolutePath());
        getXMLReader().parse(input);
        return root;
    }


    /**
     * 解析某个输入资源 比如e:\learn\tomcat\catalina-home\conf\server.xml对应的资源
     * @param input 输入资源
     * @return
     * @throws IOException
     * @throws SAXException
     */
    public Object parse(InputSource input) throws IOException, SAXException {
        configure();
        //获取xml阅读器 解析.xml文件
        getXMLReader().parse(input);
        //返回数组栈的根元素
        return root;
    }


    /**
     * Parse the content of the specified input stream using this Digester.
     * Returns the root element from the object stack (if any).
     *
     * @param input Input stream containing the XML data to be parsed
     * @return the root object
     * @exception IOException if an input/output error occurs
     * @exception SAXException if a parsing exception occurs
     */
    public Object parse(InputStream input) throws IOException, SAXException {
        configure();
        InputSource is = new InputSource(input);
        getXMLReader().parse(is);
        return root;
    }


    /**
     * <p>Register the specified DTD URL for the specified public identifier.
     * This must be called before the first call to <code>parse()</code>.
     * </p><p>
     * <code>Digester</code> contains an internal <code>EntityResolver</code>
     * implementation. This maps <code>PUBLICID</code>'s to URLs
     * (from which the resource will be loaded). A common use case for this
     * method is to register local URLs (possibly computed at runtime by a
     * classloader) for DTDs. This allows the performance advantage of using
     * a local version without having to ensure every <code>SYSTEM</code>
     * URI on every processed xml document is local. This implementation provides
     * only basic functionality. If more sophisticated features are required,
     * using {@link #setEntityResolver} to set a custom resolver is recommended.
     * </p><p>
     * <strong>Note:</strong> This method will have no effect when a custom
     * <code>EntityResolver</code> has been set. (Setting a custom
     * <code>EntityResolver</code> overrides the internal implementation.)
     * </p>
     * @param publicId Public identifier of the DTD to be resolved
     * @param entityURL The URL to use for reading this DTD
     */
    public void register(String publicId, String entityURL) {

        if (log.isDebugEnabled()) {
            log.debug("register('" + publicId + "', '" + entityURL + "'");
        }
        entityValidator.put(publicId, entityURL);

    }


    // --------------------------------------------------------- Rule Methods


    /**
     * 添加规则
     * @param pattern 匹配正则
     * @param rule 规则
     */
    public void addRule(String pattern, Rule rule) {

        //设置digester对象
        rule.setDigester(this);
        //获取规则列表 添加正则对应的规则
        getRules().add(pattern, rule);

    }


    /**
     * Register a set of Rule instances defined in a RuleSet.
     *
     * @param ruleSet The RuleSet instance to configure from
     */
    public void addRuleSet(RuleSet ruleSet) {

        String oldNamespaceURI = getRuleNamespaceURI();
        @SuppressWarnings("deprecation")
        String newNamespaceURI = ruleSet.getNamespaceURI();
        if (log.isDebugEnabled()) {
            if (newNamespaceURI == null) {
                log.debug("addRuleSet() with no namespace URI");
            } else {
                log.debug("addRuleSet() with namespace URI " + newNamespaceURI);
            }
        }
        setRuleNamespaceURI(newNamespaceURI);
        ruleSet.addRuleInstances(this);
        setRuleNamespaceURI(oldNamespaceURI);
    }


    /**
     * Add an "call method" rule for a method which accepts no arguments.
     *
     * @param pattern Element matching pattern
     * @param methodName Method name to be called
     * @see CallMethodRule
     */
    public void addCallMethod(String pattern, String methodName) {

        addRule(pattern, new CallMethodRule(methodName));

    }

    /**
     * Add an "call method" rule for the specified parameters.
     *
     * @param pattern Element matching pattern
     * @param methodName Method name to be called
     * @param paramCount Number of expected parameters (or zero
     *  for a single parameter from the body of this element)
     * @see CallMethodRule
     */
    public void addCallMethod(String pattern, String methodName, int paramCount) {

        addRule(pattern, new CallMethodRule(methodName, paramCount));

    }


    /**
     * Add a "call parameter" rule for the specified parameters.
     *
     * @param pattern Element matching pattern
     * @param paramIndex Zero-relative parameter index to set
     *  (from the body of this element)
     * @see CallParamRule
     */
    public void addCallParam(String pattern, int paramIndex) {

        addRule(pattern, new CallParamRule(paramIndex));

    }


    /**
     * Add a "factory create" rule for the specified parameters.
     *
     * @param pattern Element matching pattern
     * @param creationFactory Previously instantiated ObjectCreationFactory
     *  to be utilized
     * @param ignoreCreateExceptions when <code>true</code> any exceptions thrown during
     * object creation will be ignored.
     * @see FactoryCreateRule
     */
    public void addFactoryCreate(String pattern, ObjectCreationFactory creationFactory,
            boolean ignoreCreateExceptions) {

        creationFactory.setDigester(this);
        addRule(pattern, new FactoryCreateRule(creationFactory, ignoreCreateExceptions));

    }

    /**
     * Add an "object create" rule for the specified parameters.
     *
     * @param pattern Element matching pattern
     * @param className Java class name to be created
     * @see ObjectCreateRule
     */
    public void addObjectCreate(String pattern, String className) {

        addRule(pattern, new ObjectCreateRule(className));

    }


    /**
     * 添加对象创建鬼咋
     * @param pattern 匹配正则字符串 Server
     * @param className class类全类名 比如 org.apache.catalina.core.StandardServer
     * @param attributeName 属性名className
     */
    public void addObjectCreate(String pattern, String className, String attributeName) {

        //添加规则
        addRule(pattern, new ObjectCreateRule(className, attributeName));

    }


    /**
     * Add a "set next" rule for the specified parameters.
     *
     * @param pattern Element matching pattern
     * @param methodName Method name to call on the parent element
     * @param paramType Java class name of the expected parameter type
     *  (if you wish to use a primitive type, specify the corresponding
     *  Java wrapper class instead, such as <code>java.lang.Boolean</code>
     *  for a <code>boolean</code> parameter)
     * @see SetNextRule
     */
    public void addSetNext(String pattern, String methodName, String paramType) {

        addRule(pattern, new SetNextRule(methodName, paramType));

    }

    /**
     * 设置属性
     * @param pattern 匹配正则
     */
    public void addSetProperties(String pattern) {
        //添加一个set属性的规则
        addRule(pattern, new SetPropertiesRule());

    }


    // --------------------------------------------------- Object Stack Methods


    /**
     * Clear the current contents of the object stack.
     * <p>
     * Calling this method <i>might</i> allow another document of the same type
     * to be correctly parsed. However this method was not intended for this
     * purpose. In general, a separate Digester object should be created for
     * each document to be parsed.
     */
    public void clear() {

        match = "";
        bodyTexts.clear();
        params.clear();
        publicId = null;
        stack.clear();
        log = null;
        saxLog = null;
        configured = false;

    }


    public void reset() {
        root = null;
        setErrorHandler(null);
        clear();
    }


    /**
     * Return the top object on the stack without removing it.  If there are
     * no objects on the stack, return <code>null</code>.
     * @return the top object
     */
    public Object peek() {
        try {
            return stack.peek();
        } catch (EmptyStackException e) {
            log.warn("Empty stack (returning null)");
            return null;
        }
    }


    /**
     * Return the n'th object down the stack, where 0 is the top element
     * and [getCount()-1] is the bottom element.  If the specified index
     * is out of range, return <code>null</code>.
     *
     * @param n Index of the desired element, where 0 is the top of the stack,
     *  1 is the next element down, and so on.
     * @return the specified object
     */
    public Object peek(int n) {
        try {
            return stack.peek(n);
        } catch (EmptyStackException e) {
            log.warn("Empty stack (returning null)");
            return null;
        }
    }


    /**
     * Pop the top object off of the stack, and return it.  If there are
     * no objects on the stack, return <code>null</code>.
     * @return the top object
     */
    public Object pop() {
        try {
            return stack.pop();
        } catch (EmptyStackException e) {
            log.warn("Empty stack (returning null)");
            return null;
        }
    }


    /**
     * Push a new object onto the top of the object stack.
     *
     * @param object The new object
     */
    public void push(Object object) {

        if (stack.size() == 0) {
            root = object;
        }
        stack.push(object);

    }

    /**
     * When the Digester is being used as a SAXContentHandler,
     * this method allows you to access the root object that has been
     * created after parsing.
     *
     * @return the root object that has been created after parsing
     *  or null if the digester has not parsed any XML yet.
     */
    public Object getRoot() {
        return root;
    }


    // ------------------------------------------------ Parameter Stack Methods


    // ------------------------------------------------------ Protected Methods


    /**
     * <p>
     * Provide a hook for lazy configuration of this <code>Digester</code>
     * instance.  The default implementation does nothing, but subclasses
     * can override as needed.
     * </p>
     *
     * <p>
     * <strong>Note</strong> This method may be called more than once.
     * </p>
     */
    protected void configure() {

        // Do not configure more than once
        if (configured) {
            return;
        }

        log = LogFactory.getLog("org.apache.tomcat.util.digester.Digester");
        saxLog = LogFactory.getLog("org.apache.tomcat.util.digester.Digester.sax");

        // Set the configuration flag to avoid repeating
        configured = true;
    }


    /**
     * <p>Return the top object on the parameters stack without removing it.  If there are
     * no objects on the stack, return <code>null</code>.</p>
     *
     * <p>The parameters stack is used to store <code>CallMethodRule</code> parameters.
     * See {@link #params}.</p>
     * @return the top object on the parameters stack
     */
    public Object peekParams() {
        try {
            return params.peek();
        } catch (EmptyStackException e) {
            log.warn("Empty stack (returning null)");
            return null;
        }
    }


    /**
     * <p>Pop the top object off of the parameters stack, and return it.  If there are
     * no objects on the stack, return <code>null</code>.</p>
     *
     * <p>The parameters stack is used to store <code>CallMethodRule</code> parameters.
     * See {@link #params}.</p>
     * @return the top object on the parameters stack
     */
    public Object popParams() {
        try {
            if (log.isTraceEnabled()) {
                log.trace("Popping params");
            }
            return params.pop();
        } catch (EmptyStackException e) {
            log.warn("Empty stack (returning null)");
            return null;
        }
    }


    /**
     * <p>Push a new object onto the top of the parameters stack.</p>
     *
     * <p>The parameters stack is used to store <code>CallMethodRule</code> parameters.
     * See {@link #params}.</p>
     *
     * @param object The new object
     */
    public void pushParams(Object object) {
        if (log.isTraceEnabled()) {
            log.trace("Pushing params");
        }
        params.push(object);

    }

    /**
     * Create a SAX exception which also understands about the location in
     * the digester file where the exception occurs
     * @param message The error message
     * @param e The root cause
     * @return the new exception
     */
    public SAXException createSAXException(String message, Exception e) {
        if ((e != null) && (e instanceof InvocationTargetException)) {
            Throwable t = e.getCause();
            if (t instanceof ThreadDeath) {
                throw (ThreadDeath) t;
            }
            if (t instanceof VirtualMachineError) {
                throw (VirtualMachineError) t;
            }
            if (t instanceof Exception) {
                e = (Exception) t;
            }
        }
        if (locator != null) {
            String error = "Error at (" + locator.getLineNumber() + ", " + locator.getColumnNumber()
                    + ") : " + message;
            if (e != null) {
                return new SAXParseException(error, locator, e);
            } else {
                return new SAXParseException(error, locator);
            }
        }
        log.error("No Locator!");
        if (e != null) {
            return new SAXException(message, e);
        } else {
            return new SAXException(message);
        }
    }

    /**
     * Create a SAX exception which also understands about the location in
     * the digester file where the exception occurs
     * @param e The root cause
     * @return the new exception
     */
    public SAXException createSAXException(Exception e) {
        if (e instanceof InvocationTargetException) {
            Throwable t = e.getCause();
            if (t instanceof ThreadDeath) {
                throw (ThreadDeath) t;
            }
            if (t instanceof VirtualMachineError) {
                throw (VirtualMachineError) t;
            }
            if (t instanceof Exception) {
                e = (Exception) t;
            }
        }
        return createSAXException(e.getMessage(), e);
    }

    /**
     * Create a SAX exception which also understands about the location in
     * the digester file where the exception occurs
     * @param message The error message
     * @return the new exception
     */
    public SAXException createSAXException(String message) {
        return createSAXException(message, null);
    }


    // ------------------------------------------------------- Private Methods


    /**
     * 更新属性列表
     * @param list
     * @return
     */
    private Attributes updateAttributes(Attributes list) {

        if (list.getLength() == 0) {//没有属性
            return list;
        }

        //属性实现
        AttributesImpl newAttrs = new AttributesImpl(list);
        //属性数量
        int nAttributes = newAttrs.getLength();
        for (int i = 0; i < nAttributes; ++i) {//获取每一个属性值
            String value = newAttrs.getValue(i);
            try {
                //设置属性下标对应的值
                newAttrs.setValue(i, IntrospectionUtils.replaceProperties(value, null, source, getClassLoader()).intern());
            } catch (Exception e) {
                log.warn(sm.getString("digester.failedToUpdateAttributes", newAttrs.getLocalName(i), value), e);
            }
        }

        return newAttrs;
    }


    /**
     * Return a new StringBuilder containing the same contents as the
     * input buffer, except that data of form ${varname} have been
     * replaced by the value of that var as defined in the system property.
     */
    private StringBuilder updateBodyText(StringBuilder bodyText) {
        String in = bodyText.toString();
        String out;
        try {
            out = IntrospectionUtils.replaceProperties(in, null, source, getClassLoader());
        } catch (Exception e) {
            return bodyText; // return unchanged data
        }

        if (out == in) {
            // No substitutions required. Don't waste memory creating
            // a new buffer
            return bodyText;
        } else {
            return new StringBuilder(out);
        }
    }


    /**
     * EntityResolver实体解析器包装对象类
     */
    private static class EntityResolverWrapper implements EntityResolver {

        /**
         * EntityResolver实体解析器
         */
        private final EntityResolver entityResolver;

        /**
         * 属性资源数组
         */
        private final PropertySource[] source;

        /**
         * 类加载器
         */
        private final ClassLoader classLoader;

        /**
         * 实例化一个EntityResolver实体解析器对象
         * @param entityResolver 实体解析器对象
         * @param source 资源
         * @param classLoader 类加载器
         */
        public EntityResolverWrapper(EntityResolver entityResolver, PropertySource[] source, ClassLoader classLoader) {
            //设置实体解析器
            this.entityResolver = entityResolver;
            //设置资源
            this.source = source;
            //设置类加载器
            this.classLoader = classLoader;
        }

        @Override
        public InputSource resolveEntity(String publicId, String systemId)
                throws SAXException, IOException {
            publicId = replace(publicId);
            systemId = replace(systemId);
            return entityResolver.resolveEntity(publicId, systemId);
        }

        protected String replace(String input) {
            try {
                return IntrospectionUtils.replaceProperties(input, null, source, classLoader);
            } catch (Exception e) {
                return input;
            }
        }
    }


    /**
     * EntrtyResovler2实体解析器的包装对象
     */
    private static class EntityResolver2Wrapper extends EntityResolverWrapper implements EntityResolver2 {

        /**
         * 被包装的原始EntiryResolver2对象
         */
        private final EntityResolver2 entityResolver2;

        /**
         * 实例一个实体解析器包装对象
         * @param entityResolver 被包装的实体解析器EntityResolver2对象
         * @param source 属性资源
         * @param classLoader 类加载器
         */
        public EntityResolver2Wrapper(EntityResolver2 entityResolver, PropertySource[] source,
                ClassLoader classLoader) {
            super(entityResolver, source, classLoader);
            this.entityResolver2 = entityResolver;
        }

        @Override
        public InputSource getExternalSubset(String name, String baseURI)
                throws SAXException, IOException {
            name = replace(name);
            baseURI = replace(baseURI);
            return entityResolver2.getExternalSubset(name, baseURI);
        }

        @Override
        public InputSource resolveEntity(String name, String publicId, String baseURI,
                String systemId) throws SAXException, IOException {
            name = replace(name);
            publicId = replace(publicId);
            baseURI = replace(baseURI);
            systemId = replace(systemId);
            return entityResolver2.resolveEntity(name, publicId, baseURI, systemId);
        }
    }
}
