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
package org.apache.catalina.valves;


import java.io.BufferedWriter;
import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.catalina.LifecycleException;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.B2CConverter;

/**
 * 访问日志的阀门类
 */
public class AccessLogValve extends AbstractAccessLogValve {

    private static final Log log = LogFactory.getLog(AccessLogValve.class);

    //------------------------------------------------------ Constructor
    /**
     * 实例化一个访问日志的闸门对象
     */
    public AccessLogValve() {
        super();
    }

    // ----------------------------------------------------- Instance Variables


    /**
     * 日期格式化后的时间戳 比如.2021-03-08
     */
    private volatile String dateStamp = "";


    /**
     * 文件夹
     */
    private String directory = "logs";

    /**
     * 访问日志的前缀
     */
    protected volatile String prefix = "access_log";


    /**
     * Should we rotate our log file? Default is true (like old behavior)
     */
    protected boolean rotatable = true;

    /**
     * Should we defer inclusion of the date stamp in the file
     * name until rotate time? Default is false.
     */
    protected boolean renameOnRotate = false;


    /**
     * Buffered logging.
     */
    private boolean buffered = true;


    /**
     * 访问日志的后缀
     */
    protected volatile String suffix = "";


    /**
     * 向当前日志文件写入字节的对象
     */
    protected PrintWriter writer = null;


    /**
     * A date formatter to format a Date using the format
     * given by <code>fileDateFormat</code>.
     */
    protected SimpleDateFormat fileDateFormatter = null;


    /**
     * 当前日志文件对象 catalina-home/logs/access_logs.2021-03-08.txt
     */
    protected File currentLogFile = null;

    /**
     * Instant when the log daily rotation was last checked.
     */
    private volatile long rotationLastChecked = 0L;

    /**
     * Do we check for log file existence? Helpful if an external
     * agent renames the log file so we can automagically recreate it.
     */
    private boolean checkExists = false;

    /**
     * 文件日志序序列化字符串
     */
    protected String fileDateFormat = ".yyyy-MM-dd";

    /**
     * Character set used by the log file. If it is <code>null</code>, the
     * system default character set will be used. An empty string will be
     * treated as <code>null</code> when this property is assigned.
     */
    protected volatile String encoding = null;

    /**
     * The number of days to retain the access log files before they are
     * removed.
     */
    private int maxDays = -1;

    /**
     * 默认为true
     */
    private volatile boolean checkForOldLogs = false;

    // ------------------------------------------------------------- Properties


    public int getMaxDays() {
        return maxDays;
    }


    public void setMaxDays(int maxDays) {
        this.maxDays = maxDays;
    }


    /**
     * @return the directory in which we create log files.
     */
    public String getDirectory() {
        return directory;
    }


    /**
     * 设置文件夹
     * @param directory 文件夹对象
     */
    public void setDirectory(String directory) {
        this.directory = directory;
    }

    /**
     * Check for file existence before logging.
     * @return <code>true</code> if file existence is checked first
     */
    public boolean isCheckExists() {

        return checkExists;

    }


    /**
     * Set whether to check for log file existence before logging.
     *
     * @param checkExists true meaning to check for file existence.
     */
    public void setCheckExists(boolean checkExists) {

        this.checkExists = checkExists;

    }


    /**
     * @return the log file prefix.
     */
    public String getPrefix() {
        return prefix;
    }


    /**
     * 设置访问日志的前缀
     * @param prefix 前缀
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }


    /**
     * Should we rotate the access log.
     *
     * @return <code>true</code> if the access log should be rotated
     */
    public boolean isRotatable() {
        return rotatable;
    }


    /**
     * Configure whether the access log should be rotated.
     *
     * @param rotatable true if the log should be rotated
     */
    public void setRotatable(boolean rotatable) {
        this.rotatable = rotatable;
    }


    /**
     * Should we defer inclusion of the date stamp in the file
     * name until rotate time.
     * @return <code>true</code> if the logs file names are time stamped
     *  only when they are rotated
     */
    public boolean isRenameOnRotate() {
        return renameOnRotate;
    }


    /**
     * Set the value if we should defer inclusion of the date
     * stamp in the file name until rotate time
     *
     * @param renameOnRotate true if defer inclusion of date stamp
     */
    public void setRenameOnRotate(boolean renameOnRotate) {
        this.renameOnRotate = renameOnRotate;
    }


    /**
     * Is the logging buffered. Usually buffering can increase performance.
     * @return <code>true</code> if the logging uses a buffer
     */
    public boolean isBuffered() {
        return buffered;
    }


    /**
     * Set the value if the logging should be buffered
     *
     * @param buffered <code>true</code> if buffered.
     */
    public void setBuffered(boolean buffered) {
        this.buffered = buffered;
    }


    /**
     * @return the log file suffix.
     */
    public String getSuffix() {
        return suffix;
    }


    /**
     * 设置访问日志的后缀
     * @param suffix 后缀字符串
     */
    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    /**
     * @return the date format date based log rotation.
     */
    public String getFileDateFormat() {
        return fileDateFormat;
    }


    /**
     * Set the date format date based log rotation.
     * @param fileDateFormat The format for the file timestamp
     */
    public void setFileDateFormat(String fileDateFormat) {
        String newFormat;
        if (fileDateFormat == null) {
            newFormat = "";
        } else {
            newFormat = fileDateFormat;
        }
        this.fileDateFormat = newFormat;

        synchronized (this) {
            fileDateFormatter = new SimpleDateFormat(newFormat, Locale.US);
            fileDateFormatter.setTimeZone(TimeZone.getDefault());
        }
    }

    /**
     * Return the character set name that is used to write the log file.
     *
     * @return Character set name, or <code>null</code> if the system default
     *  character set is used.
     */
    public String getEncoding() {
        return encoding;
    }

    /**
     * Set the character set that is used to write the log file.
     *
     * @param encoding The name of the character set.
     */
    public void setEncoding(String encoding) {
        if (encoding != null && encoding.length() > 0) {
            this.encoding = encoding;
        } else {
            this.encoding = null;
        }
    }

    // --------------------------------------------------------- Public Methods

    /**
     * Execute a periodic task, such as reloading, etc. This method will be
     * invoked inside the classloading context of this container. Unexpected
     * throwables will be caught and logged.
     */
    @Override
    public synchronized void backgroundProcess() {
        if (getState().isAvailable() && getEnabled() && writer != null &&
                buffered) {
            writer.flush();
        }

        int maxDays = this.maxDays;
        String prefix = this.prefix;
        String suffix = this.suffix;

        if (rotatable && checkForOldLogs && maxDays > 0) {
            long deleteIfLastModifiedBefore =
                    System.currentTimeMillis() - (maxDays * 24L * 60 * 60 * 1000);
            File dir = getDirectoryFile();
            if (dir.isDirectory()) {
                String[] oldAccessLogs = dir.list();

                if (oldAccessLogs != null) {
                    for (String oldAccessLog : oldAccessLogs) {
                        boolean match = false;

                        if (prefix != null && prefix.length() > 0) {
                            if (!oldAccessLog.startsWith(prefix)) {
                                continue;
                            }
                            match = true;
                        }

                        if (suffix != null && suffix.length() > 0) {
                            if (!oldAccessLog.endsWith(suffix)) {
                                continue;
                            }
                            match = true;
                        }

                        if (match) {
                            File file = new File(dir, oldAccessLog);
                            if (file.isFile() && file.lastModified() < deleteIfLastModifiedBefore) {
                                if (!file.delete()) {
                                    log.warn(sm.getString(
                                            "accessLogValve.deleteFail", file.getAbsolutePath()));
                                }
                            }
                        }
                    }
                }
            }
            checkForOldLogs = false;
        }
    }

    /**
     * Rotate the log file if necessary.
     */
    public void rotate() {
        if (rotatable) {
            // Only do a logfile switch check once a second, max.
            long systime = System.currentTimeMillis();
            if ((systime - rotationLastChecked) > 1000) {
                synchronized(this) {
                    if ((systime - rotationLastChecked) > 1000) {
                        rotationLastChecked = systime;

                        String tsDate;
                        // Check for a change of date
                        tsDate = fileDateFormatter.format(new Date(systime));

                        // If the date has changed, switch log files
                        if (!dateStamp.equals(tsDate)) {
                            close(true);
                            dateStamp = tsDate;
                            open();
                        }
                    }
                }
            }
        }
    }

    /**
     * Rename the existing log file to something else. Then open the
     * old log file name up once again. Intended to be called by a JMX
     * agent.
     *
     * @param newFileName The file name to move the log file entry to
     * @return true if a file was rotated with no error
     */
    public synchronized boolean rotate(String newFileName) {

        if (currentLogFile != null) {
            File holder = currentLogFile;
            close(false);
            try {
                holder.renameTo(new File(newFileName));
            } catch (Throwable e) {
                ExceptionUtils.handleThrowable(e);
                log.error(sm.getString("accessLogValve.rotateFail"), e);
            }

            /* Make sure date is correct */
            dateStamp = fileDateFormatter.format(
                    new Date(System.currentTimeMillis()));

            open();
            return true;
        } else {
            return false;
        }

    }

    // -------------------------------------------------------- Private Methods


    /**
     * 获取日志文件夹 catalina-home/logs/access_logs.2021-03-08.txt
     * @return
     */
    private File getDirectoryFile() {
        //logs文件夹
        File dir = new File(directory);
        if (!dir.isAbsolute()) {//不是绝对路径
            //文件夹路径 catalina-home/home
            dir = new File(getContainer().getCatalinaBase(), directory);
        }
        return dir;
    }


    /**
     * 获取日志文件
     * @param useDateStamp 是否使用日期戳
     * @return
     */
    private File getLogFile(boolean useDateStamp) {
        // 获取catalina-home/logs文件夹
        File dir = getDirectoryFile();
        if (!dir.mkdirs() && !dir.isDirectory()) {//文件夹不存在 抛出异常
            log.error(sm.getString("accessLogValve.openDirFail", dir));
        }

        // Calculate the current log file name
        File pathname;
        if (useDateStamp) {//使用日志时间戳
            //文件路径 catalina-home/logs/access_logs.2021-03-08.txt
            pathname = new File(dir.getAbsoluteFile(), prefix + dateStamp
                    + suffix);
        } else {
            pathname = new File(dir.getAbsoluteFile(), prefix + suffix);
        }

        //父目录 catalina-home/logs
        File parent = pathname.getParentFile();
        if (!parent.mkdirs() && !parent.isDirectory()) {
            log.error(sm.getString("accessLogValve.openDirFail", parent));
        }

        // catalina-home/logs/access_logs.2021-03-08.txt
        return pathname;
    }

    /**
     * Move a current but rotated log file back to the unrotated
     * one. Needed if date stamp inclusion is deferred to rotation
     * time.
     */
    private void restore() {
        File newLogFile = getLogFile(false);
        File rotatedLogFile = getLogFile(true);
        if (rotatedLogFile.exists() && !newLogFile.exists() &&
            !rotatedLogFile.equals(newLogFile)) {
            try {
                if (!rotatedLogFile.renameTo(newLogFile)) {
                    log.error(sm.getString("accessLogValve.renameFail", rotatedLogFile, newLogFile));
                }
            } catch (Throwable e) {
                ExceptionUtils.handleThrowable(e);
                log.error(sm.getString("accessLogValve.renameFail", rotatedLogFile, newLogFile), e);
            }
        }
    }


    /**
     * Close the currently open log file (if any)
     *
     * @param rename Rename file to final name after closing
     */
    private synchronized void close(boolean rename) {
        if (writer == null) {
            return;
        }
        writer.flush();
        writer.close();
        if (rename && renameOnRotate) {
            File newLogFile = getLogFile(true);
            if (!newLogFile.exists()) {
                try {
                    if (!currentLogFile.renameTo(newLogFile)) {
                        log.error(sm.getString("accessLogValve.renameFail", currentLogFile, newLogFile));
                    }
                } catch (Throwable e) {
                    ExceptionUtils.handleThrowable(e);
                    log.error(sm.getString("accessLogValve.renameFail", currentLogFile, newLogFile), e);
                }
            } else {
                log.error(sm.getString("accessLogValve.alreadyExists", currentLogFile, newLogFile));
            }
        }
        writer = null;
        dateStamp = "";
        currentLogFile = null;
    }


    /**
     * Log the specified message to the log file, switching files if the date
     * has changed since the previous log call.
     *
     * @param message Message to be logged
     */
    @Override
    public void log(CharArrayWriter message) {

        rotate();

        /* In case something external rotated the file instead */
        if (checkExists) {
            synchronized (this) {
                if (currentLogFile != null && !currentLogFile.exists()) {
                    try {
                        close(false);
                    } catch (Throwable e) {
                        ExceptionUtils.handleThrowable(e);
                        log.info(sm.getString("accessLogValve.closeFail"), e);
                    }

                    /* Make sure date is correct */
                    dateStamp = fileDateFormatter.format(
                            new Date(System.currentTimeMillis()));

                    open();
                }
            }
        }

        // Log this message
        try {
            message.write(System.lineSeparator());
            synchronized(this) {
                if (writer != null) {
                    message.writeTo(writer);
                    if (!buffered) {
                        writer.flush();
                    }
                }
            }
        } catch (IOException ioe) {
            log.warn(sm.getString(
                    "accessLogValve.writeFail", message.toString()), ioe);
        }
    }


    /**
     * 打开访问日志阀门对象
     */
    protected synchronized void open() {
        // Open the current log file
        //获取当前日志文件路径：catalina-home/logs/access_logs.2021-03-08.txt
        File pathname = getLogFile(rotatable && !renameOnRotate);

        //字符集
        Charset charset = null;
        if (encoding != null) {
            try {
                charset = B2CConverter.getCharset(encoding);
            } catch (UnsupportedEncodingException ex) {
                log.error(sm.getString(
                        "accessLogValve.unsupportedEncoding", encoding), ex);
            }
        }
        if (charset == null) {
            //使用ISO_8859_1字符集
            charset = StandardCharsets.ISO_8859_1;
        }

        try {
            //获取向日志文件中写入字节对象
            writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(pathname, true), charset), 128000),
                    false);

            //当前日志文件
            currentLogFile = pathname;
        } catch (IOException e) {
            writer = null;
            currentLogFile = null;
            log.error(sm.getString("accessLogValve.openFail", pathname, System.getProperty("user.name")), e);
        }
        // Rotating a log file will always trigger a new file to be opened so
        // when a new file is opened, check to see if any old files need to be
        // removed.
        checkForOldLogs = true;
    }

    /**
     * 启动访问日志阀门对象
     * @throws LifecycleException
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        //获取文件日期序列化格式
        String format = getFileDateFormat();
        //获取序列化对象
        fileDateFormatter = new SimpleDateFormat(format, Locale.US);
        //设置时间起点
        fileDateFormatter.setTimeZone(TimeZone.getDefault());
        //序列化当前时间 .2021-03-08
        dateStamp = fileDateFormatter.format(new Date(System.currentTimeMillis()));
        if (rotatable && renameOnRotate) {
            restore();
        }

        //打开阀门
        open();

        super.startInternal();
    }


    /**
     * Stop this component and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        super.stopInternal();
        close(false);
    }
}
