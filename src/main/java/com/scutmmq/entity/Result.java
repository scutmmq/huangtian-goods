package com.scutmmq.entity;
import lombok.Data;


@Data
public class Result {
    private Integer code;
    private String msg;
    private  Object data;

    public  static  Result success(){
        Result result = new Result();
        result.code = 1;
        result.msg="success";
        return result;
    }

    public  static  Result success(Object object){
        Result result = success();
        result.data = object;
        return result;
    }

    /**
     * 显式指定 code 的 success 构造,用于 /dev/ai/eval/run 这种
     * 调用方需要根据结果调整 http code 的场景(code=0 表示有 case 失败)。
     */
    public static Result successWithCode(int code, String msg, Object data) {
        Result result = new Result();
        result.code = code;
        result.msg = msg;
        result.data = data;
        return result;
    }

    public static Result error(String msg) {
        Result result = new Result();
        result.msg = msg;
        result.code = 0;
        return result;
    }
}

