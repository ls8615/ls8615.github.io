package cvmes.common;

import cvmes.CvmesService;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

public class HotLoaderServicePackge {
    private ClassLoader parent;
    private ClassLoader currentClassLoader;

    /**
     * 服务包名
     */
    private String packageName;

    /**
     * 服务启动类名
     */
    private String className;

    /**
     * 服务类全名，包名+类名
     */
    private String fullClassName;

    /**
     * 服务包名路径
     */
    private String packagePath;

    private String classPath;

    private int hotType;

    /**
     * 构造函数
     *
     * @param packageName 服务包名
     * @param className   服务类名
     */
    public HotLoaderServicePackge(String packageName, String className, int hotType) throws UnsupportedEncodingException {
        this.packageName = packageName;
        this.className = className;
        this.hotType = hotType;
        this.fullClassName = String.format("%s.%s", packageName, className);
        this.classPath = URLDecoder.decode(this.getClass().getResource("/").getPath(), "UTF-8");

        this.packagePath = String.format("%s%s", classPath, packageName.replace(".", "/"));

        this.parent = CvmesService.class.getClassLoader();

        this.currentClassLoader = new HotClassLoader();
    }

    /**
     * 获取当前服务class loader
     *
     * @return
     */
    public ClassLoader getCurrentClassLoader() {
        return this.currentClassLoader;
    }

    /**
     * 重新加载当前服务class loader
     *
     * @return
     */
    public ClassLoader replaceClassLoader() {
        currentClassLoader = new HotClassLoader();
        return currentClassLoader;
    }

    public Thread getStartThread()
            throws ClassNotFoundException,
            NoSuchMethodException,
            InstantiationException,
            IllegalAccessException,
            InvocationTargetException {
        if (hotType == 1) {
            return getStartThreadOfPackge();
        }

        if (hotType == 0) {
            return getStartThreadOfClass();
        }

        throw new ClassNotFoundException(String.format("选择热启动类型线程对象异常，不存在的热启动类型，热加载类型值[%s]", hotType));
    }

    /**
     * 通过服务包名和服务类名，获取子服务线程类对象
     *
     * @return
     * @throws ClassNotFoundException
     * @throws NoSuchMethodException
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    private Thread getStartThreadOfPackge()
            throws ClassNotFoundException,
            NoSuchMethodException,
            InstantiationException,
            IllegalAccessException,
            InvocationTargetException {

        //1.获取服务包名和子包下的所有class文件
        List<File> files = new ArrayList<>();
        getFileList(this.packagePath, files);

        //2.重新加载所有类
        Class cls = null;
        Class subCls = null;
        for (File sub : files) {
            subCls = ((HotClassLoader) this.currentClassLoader).findClass(sub.getPath(), true);
            if (subCls.getName().substring(subCls.getName().lastIndexOf(".") + 1).equals(this.className))
                cls = subCls;
        }

        //3.找不到启动类抛出异常
        if (cls == null) {
            throw new ClassNotFoundException(String.format("找不到指定类[%s]", fullClassName));
        }

        Class<Thread> subServiceThreadClass = (Class<Thread>) cls;
        Thread subServiceThread = subServiceThreadClass.getConstructor().newInstance();

        return subServiceThread;
    }

    private Thread getStartThreadOfClass()
            throws ClassNotFoundException,
            NoSuchMethodException,
            InstantiationException,
            IllegalAccessException,
            InvocationTargetException {
        //1.获取服务包名和子包下的所有class文件
        List<File> files = new ArrayList<>();
        getFileList(this.packagePath, files);

        //2.重新加载所有类
        Class cls = null;
        Class subCls = null;
        for (File sub : files) {
            if (hotType == 0) {
                if (sub.getName().indexOf(this.className) == -1) {
                    continue;
                }
            }

            subCls = ((HotClassLoader) this.currentClassLoader).findClass(sub.getPath(), true);
            if (subCls.getName().substring(subCls.getName().lastIndexOf(".") + 1).equals(this.className))
                cls = subCls;
        }

        //3.找不到启动类抛出异常
        if (cls == null) {
            throw new ClassNotFoundException(String.format("找不到指定类[%s]", fullClassName));
        }

        Class<Thread> subServiceThreadClass = (Class<Thread>) cls;
        Thread subServiceThread = subServiceThreadClass.getConstructor().newInstance();

        return subServiceThread;
    }

    /**
     * 获取子服务线程类
     *
     * @return
     * @throws ClassNotFoundException
     */
    public Class<?> getStartClass() throws ClassNotFoundException {
        //1.热加载指定服务的子类
        File file = new File(this.packagePath);
        for (File sub : file.listFiles()) {
            String str1 = this.className;
            String str2 = sub.getName().substring(0, sub.getName().lastIndexOf("."));

            if (str1.length() < str2.length()) {
                if (str2.substring(0, str1.length()).equals(str1)) {
                    ((HotClassLoader) this.currentClassLoader).findClass(sub.getPath(), true);
                }
            }
        }

        //2.热加载指定服务
        for (File sub : file.listFiles()) {
            String str1 = this.className;
            String str2 = sub.getName().substring(0, sub.getName().lastIndexOf("."));

            if (str1.length() == str2.length()) {
                if (str2.substring(0, str1.length()).equals(str1)) {
                    return ((HotClassLoader) this.currentClassLoader).findClass(sub.getPath(), true);
                }
            }
        }

        throw new ClassNotFoundException(fullClassName);
    }

    /**
     * 获取所有类文件
     *
     * @param strPath
     * @param filelist
     */
    private void getFileList(String strPath, List<File> filelist) {
        File dir = new File(strPath);
        for (File sub : dir.listFiles()) {
            if (sub.isDirectory()) {
                getFileList(sub.getAbsolutePath(), filelist);
            } else if (sub.getName().endsWith("class") || sub.getName().endsWith("CLASS")) {
                if (this.hotType == 0 && sub.getName().startsWith(this.className)) {
                    filelist.add(sub);
                }

                if (this.hotType == 1) {
                    filelist.add(sub);
                }
            }
        }
    }
}
