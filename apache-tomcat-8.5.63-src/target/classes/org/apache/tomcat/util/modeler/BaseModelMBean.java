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


package org.apache.tomcat.util.modeler;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.management.Attribute;
import javax.management.AttributeChangeNotification;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InstanceNotFoundException;
import javax.management.InvalidAttributeValueException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.RuntimeErrorException;
import javax.management.RuntimeOperationsException;
import javax.management.modelmbean.InvalidTargetObjectTypeException;
import javax.management.modelmbean.ModelMBeanNotificationBroadcaster;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * 基本的MBean类
 * 比如StandardServer对象的基本模型的MBean
 */
public class BaseModelMBean implements DynamicMBean, MBeanRegistration,
        ModelMBeanNotificationBroadcaster {

    private static final Log log = LogFactory.getLog(BaseModelMBean.class);

    // ----------------------------------------------------- Instance Variables

    protected ObjectName oname=null;

    /**
     * Notification broadcaster for attribute changes.
     */
    protected BaseNotificationBroadcaster attributeBroadcaster = null;

    /**
     * Notification broadcaster for general notifications.
     */
    protected BaseNotificationBroadcaster generalBroadcaster = null;

    /**
     * 基本的MBean对象的可管理的MBean对象 比如StandardServer对象对象的在mbeans-descriptors.xml中配置的解析后存储于MBeanServer的对象
     */
    protected ManagedBean managedBean = null;

    /**
     * 资源对象 比如StandardServer对象
     */
    protected Object resource = null;

    // --------------------------------------------------- DynamicMBean Methods
    // TODO: move to ManagedBean
    static final Object[] NO_ARGS_PARAM = new Object[0];

    /**
     * 资源对象类型 比如StandardServer的全类名
     */
    protected String resourceType = null;

    // key: operation val: invoke method
    //private Hashtable invokeAttMap=new Hashtable();

    /**
     * Obtain and return the value of a specific attribute of this MBean.
     *
     * @param name Name of the requested attribute
     *
     * @exception AttributeNotFoundException if this attribute is not
     *  supported by this MBean
     * @exception MBeanException if the initializer of an object
     *  throws an exception
     * @exception ReflectionException if a Java reflection exception
     *  occurs when invoking the getter
     */
    @Override
    public Object getAttribute(String name)
        throws AttributeNotFoundException, MBeanException,
            ReflectionException {
        // Validate the input parameters
        if (name == null)
            throw new RuntimeOperationsException
                (new IllegalArgumentException("Attribute name is null"),
                 "Attribute name is null");

        if( (resource instanceof DynamicMBean) &&
             ! ( resource instanceof BaseModelMBean )) {
            return ((DynamicMBean)resource).getAttribute(name);
        }

        Method m=managedBean.getGetter(name, this, resource);
        Object result = null;
        try {
            Class<?> declaring = m.getDeclaringClass();
            // workaround for catalina weird mbeans - the declaring class is BaseModelMBean.
            // but this is the catalina class.
            if( declaring.isAssignableFrom(this.getClass()) ) {
                result = m.invoke(this, NO_ARGS_PARAM );
            } else {
                result = m.invoke(resource, NO_ARGS_PARAM );
            }
        } catch (InvocationTargetException e) {
            Throwable t = e.getTargetException();
            if (t == null)
                t = e;
            if (t instanceof RuntimeException)
                throw new RuntimeOperationsException
                    ((RuntimeException) t, "Exception invoking method " + name);
            else if (t instanceof Error)
                throw new RuntimeErrorException
                    ((Error) t, "Error invoking method " + name);
            else
                throw new MBeanException
                    (e, "Exception invoking method " + name);
        } catch (Exception e) {
            throw new MBeanException
                (e, "Exception invoking method " + name);
        }

        // Return the results of this method invocation
        // FIXME - should we validate the return type?
        return result;
    }


    /**
     * Obtain and return the values of several attributes of this MBean.
     *
     * @param names Names of the requested attributes
     */
    @Override
    public AttributeList getAttributes(String names[]) {

        // Validate the input parameters
        if (names == null)
            throw new RuntimeOperationsException
                (new IllegalArgumentException("Attribute names list is null"),
                 "Attribute names list is null");

        // Prepare our response, eating all exceptions
        AttributeList response = new AttributeList();
        for (String name : names) {
            try {
                response.add(new Attribute(name, getAttribute(name)));
            } catch (Exception e) {
                // Not having a particular attribute in the response
                // is the indication of a getter problem
            }
        }
        return response;

    }

    /**
     * 设置可管理的MBean对象
     * @param managedBean 可管理的MBean对象
     */
    public void setManagedBean(ManagedBean managedBean) {
        this.managedBean = managedBean;
    }

    /**
     * Return the <code>MBeanInfo</code> object for this MBean.
     */
    @Override
    public MBeanInfo getMBeanInfo() {
        return managedBean.getMBeanInfo();
    }


    /**
     * Invoke a particular method on this MBean, and return any returned
     * value.
     *
     * <p><strong>IMPLEMENTATION NOTE</strong> - This implementation will
     * attempt to invoke this method on the MBean itself, or (if not
     * available) on the managed resource object associated with this
     * MBean.</p>
     *
     * @param name Name of the operation to be invoked
     * @param params Array containing the method parameters of this operation
     * @param signature Array containing the class names representing
     *  the signature of this operation
     *
     * @exception MBeanException if the initializer of an object
     *  throws an exception
     * @exception ReflectionException if a Java reflection exception
     *  occurs when invoking a method
     */
    @Override
    public Object invoke(String name, Object params[], String signature[])
        throws MBeanException, ReflectionException
    {
        if( (resource instanceof DynamicMBean) &&
             ! ( resource instanceof BaseModelMBean )) {
            return ((DynamicMBean)resource).invoke(name, params, signature);
        }

        // Validate the input parameters
        if (name == null)
            throw new RuntimeOperationsException
                (new IllegalArgumentException("Method name is null"),
                 "Method name is null");

        if( log.isDebugEnabled()) log.debug("Invoke " + name);

        Method method= managedBean.getInvoke(name, params, signature, this, resource);

        // Invoke the selected method on the appropriate object
        Object result = null;
        try {
            if( method.getDeclaringClass().isAssignableFrom( this.getClass()) ) {
                result = method.invoke(this, params );
            } else {
                result = method.invoke(resource, params);
            }
        } catch (InvocationTargetException e) {
            Throwable t = e.getTargetException();
            log.error("Exception invoking method " + name , t );
            if (t == null)
                t = e;
            if (t instanceof RuntimeException)
                throw new RuntimeOperationsException
                    ((RuntimeException) t, "Exception invoking method " + name);
            else if (t instanceof Error)
                throw new RuntimeErrorException
                    ((Error) t, "Error invoking method " + name);
            else
                throw new MBeanException
                    ((Exception)t, "Exception invoking method " + name);
        } catch (Exception e) {
            log.error("Exception invoking method " + name , e );
            throw new MBeanException
                (e, "Exception invoking method " + name);
        }

        // Return the results of this method invocation
        // FIXME - should we validate the return type?
        return result;

    }

    static Class<?> getAttributeClass(String signature)
        throws ReflectionException
    {
        if (signature.equals(Boolean.TYPE.getName()))
            return Boolean.TYPE;
        else if (signature.equals(Byte.TYPE.getName()))
            return Byte.TYPE;
        else if (signature.equals(Character.TYPE.getName()))
            return Character.TYPE;
        else if (signature.equals(Double.TYPE.getName()))
            return Double.TYPE;
        else if (signature.equals(Float.TYPE.getName()))
            return Float.TYPE;
        else if (signature.equals(Integer.TYPE.getName()))
            return Integer.TYPE;
        else if (signature.equals(Long.TYPE.getName()))
            return Long.TYPE;
        else if (signature.equals(Short.TYPE.getName()))
            return Short.TYPE;
        else {
            try {
                ClassLoader cl=Thread.currentThread().getContextClassLoader();
                if( cl!=null )
                    return cl.loadClass(signature);
            } catch( ClassNotFoundException e ) {
            }
            try {
                return Class.forName(signature);
            } catch (ClassNotFoundException e) {
                throw new ReflectionException
                    (e, "Cannot find Class for " + signature);
            }
        }
    }

    /**
     * Set the value of a specific attribute of this MBean.
     *
     * @param attribute The identification of the attribute to be set
     *  and the new value
     *
     * @exception AttributeNotFoundException if this attribute is not
     *  supported by this MBean
     * @exception MBeanException if the initializer of an object
     *  throws an exception
     * @exception ReflectionException if a Java reflection exception
     *  occurs when invoking the getter
     */
    @Override
    public void setAttribute(Attribute attribute)
        throws AttributeNotFoundException, MBeanException,
        ReflectionException
    {
        if( log.isDebugEnabled() )
            log.debug("Setting attribute " + this + " " + attribute );

        if( (resource instanceof DynamicMBean) &&
             ! ( resource instanceof BaseModelMBean )) {
            try {
                ((DynamicMBean)resource).setAttribute(attribute);
            } catch (InvalidAttributeValueException e) {
                throw new MBeanException(e);
            }
            return;
        }

        // Validate the input parameters
        if (attribute == null)
            throw new RuntimeOperationsException
                (new IllegalArgumentException("Attribute is null"),
                 "Attribute is null");

        String name = attribute.getName();
        Object value = attribute.getValue();

        if (name == null)
            throw new RuntimeOperationsException
                (new IllegalArgumentException("Attribute name is null"),
                 "Attribute name is null");

        Object oldValue=null;
        //if( getAttMap.get(name) != null )
        //    oldValue=getAttribute( name );

        Method m=managedBean.getSetter(name,this,resource);

        try {
            if( m.getDeclaringClass().isAssignableFrom( this.getClass()) ) {
                m.invoke(this, new Object[] { value });
            } else {
                m.invoke(resource, new Object[] { value });
            }
        } catch (InvocationTargetException e) {
            Throwable t = e.getTargetException();
            if (t == null)
                t = e;
            if (t instanceof RuntimeException)
                throw new RuntimeOperationsException
                    ((RuntimeException) t, "Exception invoking method " + name);
            else if (t instanceof Error)
                throw new RuntimeErrorException
                    ((Error) t, "Error invoking method " + name);
            else
                throw new MBeanException
                    (e, "Exception invoking method " + name);
        } catch (Exception e) {
            log.error("Exception invoking method " + name , e );
            throw new MBeanException
                (e, "Exception invoking method " + name);
        }
        try {
            sendAttributeChangeNotification(new Attribute( name, oldValue),
                    attribute);
        } catch(Exception ex) {
            log.error("Error sending notification " + name, ex);
        }
        //attributes.put( name, value );
//        if( source != null ) {
//            // this mbean is associated with a source - maybe we want to persist
//            source.updateField(oname, name, value);
//        }
    }

    @Override
    public String toString() {
        if( resource==null )
            return "BaseModelMbean[" + resourceType + "]";
        return resource.toString();
    }

    /**
     * Set the values of several attributes of this MBean.
     *
     * @param attributes THe names and values to be set
     *
     * @return The list of attributes that were set and their new values
     */
    @Override
    public AttributeList setAttributes(AttributeList attributes) {
        AttributeList response = new AttributeList();

        // Validate the input parameters
        if (attributes == null)
            return response;

        // Prepare and return our response, eating all exceptions
        String names[] = new String[attributes.size()];
        int n = 0;
        for (Object attribute : attributes) {
            Attribute item = (Attribute) attribute;
            names[n++] = item.getName();
            try {
                setAttribute(item);
            } catch (Exception e) {
                // Ignore all exceptions
            }
        }

        return getAttributes(names);

    }


    // ----------------------------------------------------- ModelMBean Methods


    /**
     * Get the instance handle of the object against which we execute
     * all methods in this ModelMBean management interface.
     *
     * @return the backend managed object
     * @exception InstanceNotFoundException if the managed resource object
     *  cannot be found
     * @exception InvalidTargetObjectTypeException if the managed resource
     *  object is of the wrong type
     * @exception MBeanException if the initializer of the object throws
     *  an exception
     * @exception RuntimeOperationsException if the managed resource or the
     *  resource type is <code>null</code> or invalid
     */
    public Object getManagedResource()
        throws InstanceNotFoundException, InvalidTargetObjectTypeException,
        MBeanException, RuntimeOperationsException {

        if (resource == null)
            throw new RuntimeOperationsException
                (new IllegalArgumentException("Managed resource is null"),
                 "Managed resource is null");

        return resource;

    }


    /**
     * 设置基本模型的MBean对象的资源对象
     * @param resource 资源对象
     * @param type 资源类型 （为resource的全类名）
     * @throws InstanceNotFoundException
     * @throws MBeanException
     * @throws RuntimeOperationsException
     */
    public void setManagedResource(Object resource, String type)
        throws InstanceNotFoundException,
        MBeanException, RuntimeOperationsException
    {
        if (resource == null)//resource不能为nulll
            throw new RuntimeOperationsException
                (new IllegalArgumentException("Managed resource is null"),
                 "Managed resource is null");

//        if (!"objectreference".equalsIgnoreCase(type))
//            throw new InvalidTargetObjectTypeException(type);

        //设置资源对象
        this.resource = resource;
        //设置资源类型
        this.resourceType = resource.getClass().getName();

//        // Make the resource aware of the model mbean.
//        try {
//            Method m=resource.getClass().getMethod("setModelMBean",
//                    new Class[] {ModelMBean.class});
//            if( m!= null ) {
//                m.invoke(resource, new Object[] {this});
//            }
//        } catch( NoSuchMethodException t ) {
//            // ignore
//        } catch( Throwable t ) {
//            log.error( "Can't set model mbean ", t );
//        }
    }


    // ------------------------------ ModelMBeanNotificationBroadcaster Methods


    /**
     * Add an attribute change notification event listener to this MBean.
     *
     * @param listener Listener that will receive event notifications
     * @param name Name of the attribute of interest, or <code>null</code>
     *  to indicate interest in all attributes
     * @param handback Handback object to be sent along with event
     *  notifications
     *
     * @exception IllegalArgumentException if the listener parameter is null
     */
    @Override
    public void addAttributeChangeNotificationListener
        (NotificationListener listener, String name, Object handback)
        throws IllegalArgumentException {

        if (listener == null)
            throw new IllegalArgumentException("Listener is null");
        if (attributeBroadcaster == null)
            attributeBroadcaster = new BaseNotificationBroadcaster();

        if( log.isDebugEnabled() )
            log.debug("addAttributeNotificationListener " + listener);

        BaseAttributeFilter filter = new BaseAttributeFilter(name);
        attributeBroadcaster.addNotificationListener
            (listener, filter, handback);

    }


    /**
     * Remove an attribute change notification event listener from
     * this MBean.
     *
     * @param listener The listener to be removed
     * @param name The attribute name for which no more events are required
     *
     *
     * @exception ListenerNotFoundException if this listener is not
     *  registered in the MBean
     */
    @Override
    public void removeAttributeChangeNotificationListener
        (NotificationListener listener, String name)
        throws ListenerNotFoundException {

        if (listener == null)
            throw new IllegalArgumentException("Listener is null");

        // FIXME - currently this removes *all* notifications for this listener
        if (attributeBroadcaster != null) {
            attributeBroadcaster.removeNotificationListener(listener);
        }

    }


    /**
     * Send an <code>AttributeChangeNotification</code> to all registered
     * listeners.
     *
     * @param notification The <code>AttributeChangeNotification</code>
     *  that will be passed
     *
     * @exception MBeanException if an object initializer throws an
     *  exception
     * @exception RuntimeOperationsException wraps IllegalArgumentException
     *  when the specified notification is <code>null</code> or invalid
     */
    @Override
    public void sendAttributeChangeNotification
        (AttributeChangeNotification notification)
        throws MBeanException, RuntimeOperationsException {

        if (notification == null)
            throw new RuntimeOperationsException
                (new IllegalArgumentException("Notification is null"),
                 "Notification is null");
        if (attributeBroadcaster == null)
            return; // This means there are no registered listeners
        if( log.isDebugEnabled() )
            log.debug( "AttributeChangeNotification " + notification );
        attributeBroadcaster.sendNotification(notification);

    }


    /**
     * Send an <code>AttributeChangeNotification</code> to all registered
     * listeners.
     *
     * @param oldValue The original value of the <code>Attribute</code>
     * @param newValue The new value of the <code>Attribute</code>
     *
     * @exception MBeanException if an object initializer throws an
     *  exception
     * @exception RuntimeOperationsException wraps IllegalArgumentException
     *  when the specified notification is <code>null</code> or invalid
     */
    @Override
    public void sendAttributeChangeNotification
        (Attribute oldValue, Attribute newValue)
        throws MBeanException, RuntimeOperationsException {

        // Calculate the class name for the change notification
        String type = null;
        if (newValue.getValue() != null)
            type = newValue.getValue().getClass().getName();
        else if (oldValue.getValue() != null)
            type = oldValue.getValue().getClass().getName();
        else
            return;  // Old and new are both null == no change

        AttributeChangeNotification notification =
            new AttributeChangeNotification
            (this, 1, System.currentTimeMillis(),
             "Attribute value has changed",
             oldValue.getName(), type,
             oldValue.getValue(), newValue.getValue());
        sendAttributeChangeNotification(notification);

    }


    /**
     * Send a <code>Notification</code> to all registered listeners as a
     * <code>jmx.modelmbean.general</code> notification.
     *
     * @param notification The <code>Notification</code> that will be passed
     *
     * @exception MBeanException if an object initializer throws an
     *  exception
     * @exception RuntimeOperationsException wraps IllegalArgumentException
     *  when the specified notification is <code>null</code> or invalid
     */
    @Override
    public void sendNotification(Notification notification)
        throws MBeanException, RuntimeOperationsException {

        if (notification == null)
            throw new RuntimeOperationsException
                (new IllegalArgumentException("Notification is null"),
                 "Notification is null");
        if (generalBroadcaster == null)
            return; // This means there are no registered listeners
        generalBroadcaster.sendNotification(notification);

    }


    /**
     * Send a <code>Notification</code> which contains the specified string
     * as a <code>jmx.modelmbean.generic</code> notification.
     *
     * @param message The message string to be passed
     *
     * @exception MBeanException if an object initializer throws an
     *  exception
     * @exception RuntimeOperationsException wraps IllegalArgumentException
     *  when the specified notification is <code>null</code> or invalid
     */
    @Override
    public void sendNotification(String message)
        throws MBeanException, RuntimeOperationsException {

        if (message == null)
            throw new RuntimeOperationsException
                (new IllegalArgumentException("Message is null"),
                 "Message is null");
        Notification notification = new Notification
            ("jmx.modelmbean.generic", this, 1, message);
        sendNotification(notification);

    }


    // ---------------------------------------- NotificationBroadcaster Methods


    /**
     * Add a notification event listener to this MBean.
     *
     * @param listener Listener that will receive event notifications
     * @param filter Filter object used to filter event notifications
     *  actually delivered, or <code>null</code> for no filtering
     * @param handback Handback object to be sent along with event
     *  notifications
     *
     * @exception IllegalArgumentException if the listener parameter is null
     */
    @Override
    public void addNotificationListener(NotificationListener listener,
                                        NotificationFilter filter,
                                        Object handback)
        throws IllegalArgumentException {

        if (listener == null)
            throw new IllegalArgumentException("Listener is null");

        if( log.isDebugEnabled() ) log.debug("addNotificationListener " + listener);

        if (generalBroadcaster == null)
            generalBroadcaster = new BaseNotificationBroadcaster();
        generalBroadcaster.addNotificationListener
            (listener, filter, handback);

        // We'll send the attribute change notifications to all listeners ( who care )
        // The normal filtering can be used.
        // The problem is that there is no other way to add attribute change listeners
        // to a model mbean ( AFAIK ). I suppose the spec should be fixed.
        if (attributeBroadcaster == null)
            attributeBroadcaster = new BaseNotificationBroadcaster();

        if( log.isDebugEnabled() )
            log.debug("addAttributeNotificationListener " + listener);

        attributeBroadcaster.addNotificationListener
                (listener, filter, handback);
    }


    /**
     * Return an <code>MBeanNotificationInfo</code> object describing the
     * notifications sent by this MBean.
     */
    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {

        // Acquire the set of application notifications
        MBeanNotificationInfo current[] = getMBeanInfo().getNotifications();
        MBeanNotificationInfo response[] =
            new MBeanNotificationInfo[current.length + 2];
 //       Descriptor descriptor = null;

        // Fill in entry for general notifications
//        descriptor = new DescriptorSupport
//            (new String[] { "name=GENERIC",
//                            "descriptorType=notification",
//                            "log=T",
//                            "severity=5",
//                            "displayName=jmx.modelmbean.generic" });
        response[0] = new MBeanNotificationInfo
            (new String[] { "jmx.modelmbean.generic" },
             "GENERIC",
             "Text message notification from the managed resource");
             //descriptor);

        // Fill in entry for attribute change notifications
//        descriptor = new DescriptorSupport
//            (new String[] { "name=ATTRIBUTE_CHANGE",
//                            "descriptorType=notification",
//                            "log=T",
//                            "severity=5",
//                            "displayName=jmx.attribute.change" });
        response[1] = new MBeanNotificationInfo
            (new String[] { "jmx.attribute.change" },
             "ATTRIBUTE_CHANGE",
             "Observed MBean attribute value has changed");
             //descriptor);

        // Copy remaining notifications as reported by the application
        System.arraycopy(current, 0, response, 2, current.length);
        return response;

    }


    /**
     * Remove a notification event listener from this MBean.
     *
     * @param listener The listener to be removed (any and all registrations
     *  for this listener will be eliminated)
     *
     * @exception ListenerNotFoundException if this listener is not
     *  registered in the MBean
     */
    @Override
    public void removeNotificationListener(NotificationListener listener)
        throws ListenerNotFoundException {

        if (listener == null)
            throw new IllegalArgumentException("Listener is null");

        if (generalBroadcaster != null) {
            generalBroadcaster.removeNotificationListener(listener);
        }

        if (attributeBroadcaster != null) {
            attributeBroadcaster.removeNotificationListener(listener);
        }
     }


    public String getModelerType() {
        return resourceType;
    }

    public String getClassName() {
        return getModelerType();
    }

    public ObjectName getJmxName() {
        return oname;
    }

    public String getObjectName() {
        if (oname != null) {
            return oname.toString();
        } else {
            return null;
        }
    }


    // -------------------- Registration  --------------------
    // XXX We can add some method patterns here- like setName() and
    // setDomain() for code that doesn't implement the Registration

    @Override
    public ObjectName preRegister(MBeanServer server,
                                  ObjectName name)
            throws Exception
    {
        if( log.isDebugEnabled())
            log.debug("preRegister " + resource + " " + name );
        oname=name;
        if( resource instanceof MBeanRegistration ) {
            oname = ((MBeanRegistration)resource).preRegister(server, name );
        }
        return oname;
    }

    @Override
    public void postRegister(Boolean registrationDone) {
        if( resource instanceof MBeanRegistration ) {
            ((MBeanRegistration)resource).postRegister(registrationDone);
        }
    }

    @Override
    public void preDeregister() throws Exception {
        if( resource instanceof MBeanRegistration ) {
            ((MBeanRegistration)resource).preDeregister();
        }
    }

    @Override
    public void postDeregister() {
        if( resource instanceof MBeanRegistration ) {
            ((MBeanRegistration)resource).postDeregister();
        }
    }
}
