package com.dslg.ai;

import java.util.ArrayList;
import java.util.List;

public class QuickSelect {
    public static void main(String[] args) {
        int[] num = new int[]{1, 2, 6, 9, 8};
        List<Integer> nums = new ArrayList<>();
        for(int i = 0; i < num.length; i ++){
            nums.add(num[i]);
        }
        int k = 3;
        System.out.println(findKMax(nums, k));
    }
    public static int findKMax(List<Integer> nums, int k){
        int pivot = nums.get(0);
        List<Integer> little = new ArrayList<>();
        List<Integer> equal = new ArrayList<>();
        List<Integer> big = new ArrayList<>();
        for(int i = 0; i < nums.size(); i ++){
            if(nums.get(i) > pivot){
                big.add(nums.get(i));
            }
            else if(nums.get(i) < pivot){
                little.add(nums.get(i));
            }
            else{
                equal.add(nums.get(i));
            }
        }
        if(k <= big.size()){
            return findKMax(big, k);
        }
        else if(k > big.size() + equal.size()){
            return findKMax(little, k - big.size() - equal.size());
        }
        else{
            return pivot;
        }
    }
}
