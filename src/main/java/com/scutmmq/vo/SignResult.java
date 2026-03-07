package com.scutmmq.vo;

import lombok.Data;

import java.util.List;

@Data
public class SignResult {
    private int signTotal;
    private int signContinue;
    private List<Integer> signedDays;

    public SignResult(int signTotal, int signContinue, List<Integer> signedDays) {
        this.signTotal = signTotal;
        this.signContinue = signContinue;
        this.signedDays = signedDays;
    }

    public static SignResult none(){
        return new SignResult(0,0,null);
    }

    public static SignResult of(int signTotal, int signContinue, List<Integer> signedDays){
        return new SignResult(signTotal,signContinue,signedDays);
    }
}
