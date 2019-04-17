package cvmes.common;

import com.jfinal.kit.PropKit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Log {
    public static void Write(String strServiceFlag, LogLevel logLevel, String strLogNote) {
        // 获取日志文件完整路径
        String log_path = String.format("%s/log/%s", System.getProperty("user.dir"), new SimpleDateFormat("yyyy/MM/dd").format(new Date()));

        // 如果日志文件路径不存在，则创建
        File file_path = new File(log_path);
        if (!file_path.exists() && !file_path.isDirectory()) {
            try {
                file_path.mkdirs();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 如果日志文件不存在，则创建
        File file_name = new File(String.format("%s/%s%s.log", log_path, strServiceFlag, new SimpleDateFormat("yyMMdd").format(new Date())));
        if (!file_name.exists()) {
            try {
                file_name.createNewFile();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 写入日志
        try {
            PrintStream ps = new PrintStream(new FileOutputStream(file_name, true));

            String msg="";
            switch (logLevel) {
                case Information:
                    //ps.println(String.format("【%s】【%s】：%s", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()), "提示", strLogNote));
                    msg =String.format("【%s】【%s】：%s", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()), "提示", strLogNote);
                    break;

                case Warning:
                    //ps.println(String.format("【%s】【%s】：%s", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()), "警告", strLogNote));
                    msg=String.format("【%s】【%s】：%s", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()), "警告", strLogNote);
                    break;

                case Error:
                    //ps.println(String.format("【%s】【%s】：%s", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()), "错误", strLogNote));
                   msg=String.format("【%s】【%s】：%s", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()), "错误", strLogNote);
                    break;
            }
            ps.println(msg);

            if(PropKit.getBoolean("isDebug")){
                System.out.println(msg);
            }

        } catch (Exception e) {
            e.printStackTrace();

            if(PropKit.getBoolean("isDebug")){
                System.out.println( e.getCause());
            }
        }
    }
}
