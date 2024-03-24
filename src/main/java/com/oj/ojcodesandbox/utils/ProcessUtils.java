package com.oj.ojcodesandbox.utils;

import com.oj.ojcodesandbox.model.ExecuteMessage;
import org.springframework.util.StopWatch;

import java.io.*;

public class ProcessUtils {

    public static ExecuteMessage getProcessResult(Process process, String option) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        StringBuilder msgBuilder = new StringBuilder();
        //  todo 编译错误的返回信息处理
        try {
            int exitCode = process.waitFor();
            executeMessage.setExitValue(exitCode);
            if (exitCode == 0) {
                System.out.println(option + "成功");
                //  获取进程的输出
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String msg;
                while ((msg = reader.readLine()) != null) {
                    msgBuilder.append(msg);
                    msgBuilder.append(System.getProperty("line.separator"));
                }
                executeMessage.setMessage(msgBuilder.toString());
                reader.close();
            } else {
                System.out.println(option + "失败，错误码" + exitCode);
                //  获取进程的输出
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                String errorMsg;
                while ((errorMsg = reader.readLine()) != null) {
                    msgBuilder.append(errorMsg);
                    msgBuilder.append(System.getProperty("line.separator"));
                    System.out.println(errorMsg);
                }
                executeMessage.setErrorMessage(msgBuilder.toString());
                reader.close();
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return executeMessage;
    }

    public static ExecuteMessage getInteractProcessResult(Process process, String input) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        StringBuilder msgBuilder = new StringBuilder();

        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            InputStream inputStream = process.getInputStream();
            OutputStream outputStream = process.getOutputStream();

            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
            outputStreamWriter.write(input);
            outputStreamWriter.flush();
            outputStreamWriter.close();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            String msg;
            while ((msg = reader.readLine()) != null) {
                msgBuilder.append(msg);
                msgBuilder.append(System.getProperty("line.separator"));
            }
            executeMessage.setMessage(msgBuilder.toString());

            int exitCode = process.waitFor();
            executeMessage.setExitValue(exitCode);

            reader.close();

            stopWatch.stop();
            executeMessage.setTime(stopWatch.getTotalTimeMillis());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return executeMessage;
    }
}
