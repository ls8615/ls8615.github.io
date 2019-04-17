package cvmes.common;

import cvmes.CvmesService;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class HotClassLoader extends ClassLoader {
    public HotClassLoader() {
        super(CvmesService.class.getClassLoader());
    }

    public Class findClass(String name, boolean resolve) throws ClassNotFoundException {
        try {
            Class c = findLoadedClass(name);
            if (c != null) {
                return c;
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ByteBuffer buf = ByteBuffer.allocate(1024);
            FileInputStream fis = null;
            FileChannel fc = null;

            try {
                fis = new FileInputStream(name);
                fc = fis.getChannel();

                while (fc.read(buf) > 0) {
                    buf.flip();
                    bos.write(buf.array(), 0, buf.limit());
                    buf.clear();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    bos.close();
                    fis.close();
                    fc.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return this.defineClass(null, bos.toByteArray(), 0, bos.toByteArray().length);
        } catch (Exception e) {
            throw new ClassNotFoundException(name);
        }
    }

    public Class hotLoadClass(String packageName, String className, int hotType) throws ClassNotFoundException {
        try {
            String fullClassName = packageName + "." + className;
            Class cls = null;
            if (hotType == 0) {
                cls = hotLoadSingleClass(fullClassName);
            } else {
                cls = hotLoadMultipleClass(String.format("%s%s", URLDecoder.decode(this.getClass().getResource("/").getPath(), "UTF-8"), fullClassName.substring(0, fullClassName.lastIndexOf(".")).replace('.', '/')),
                        className);
            }

            if (cls == null) {
                throw new ClassNotFoundException("");
            }

            return cls;
        } catch (Exception e) {
            throw new ClassNotFoundException(e.toString());
        }
    }

    private Class hotLoadMultipleClass(String directoryPath, String className) throws ClassNotFoundException {
        try {
            Class cls = null;
            Class subCls = null;

            // 获取服务所在的文件夹
            List<File> files = new ArrayList<>();
            getFileList(directoryPath, files);
            for (File sub : files) {
                subCls = findClass(sub.getPath());
                if (subCls.getName().substring(subCls.getName().lastIndexOf(".") + 1).equals(className))
                    cls = subCls;
            }

            return cls;
        } catch (Exception e) {
            throw new ClassNotFoundException(e.toString());
        }
    }

    private Class hotLoadSingleClass(String name) throws ClassNotFoundException {
        try {
            // 获取服务所在的文件夹
            File file = new File(String.format("%s%s", URLDecoder.decode(this.getClass().getResource("/").getPath(), "UTF-8"), name.substring(0, name.lastIndexOf(".")).replace('.', '/')));

            // 热加载指定服务的子类
            for (File sub : file.listFiles()) {
                String str1 = name.substring(name.lastIndexOf(".") + 1);
                String str2 = sub.getName().substring(0, sub.getName().lastIndexOf("."));

                if (str1.length() < str2.length()) {
                    if (str2.substring(0, str1.length()).equals(str1)) {
                        findClass(sub.getPath());
                    }
                }
            }

            // 热加载指定服务
            for (File sub : file.listFiles()) {
                String str1 = name.substring(name.lastIndexOf(".") + 1);
                String str2 = sub.getName().substring(0, sub.getName().lastIndexOf("."));

                if (str1.length() == str2.length()) {
                    if (str2.substring(0, str1.length()).equals(str1)) {
                        return findClass(sub.getPath());
                    }
                }
            }

            throw new ClassNotFoundException(name);
        } catch (Exception e) {
            throw new ClassNotFoundException(name);
        }
    }

    /**
     * 获取所有类文件
     *
     * @param strPath
     * @param filelist
     */
    public void getFileList(String strPath, List<File> filelist) {
        File dir = new File(strPath);
        for (File sub : dir.listFiles()) {
            if (sub.isDirectory()) {
                getFileList(sub.getAbsolutePath(), filelist);
            } else if (sub.getName().endsWith("class") || sub.getName().endsWith("CLASS")) {
                filelist.add(sub);
            }
        }
    }
}
