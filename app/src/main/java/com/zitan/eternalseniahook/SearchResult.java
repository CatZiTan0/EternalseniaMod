package com.zitan.eternalseniahook;
import java.util.List;
import java.util.ArrayList;

/**
 * @Author 紫檀
 * @Date 2025/03/30 17:53
 */
public class SearchResult {
    
    /*
    这里原本是为了区分读写的数值类型而设置的，后面发现游戏存档只涉及A内存范围D数值类型
    0 : byte
    1 : int
    2 : long
    3 : none
    */
    public static int byteType = 0;
    public static int intType = 1;
    public static int longType = 2;
    public static int noType = 3;
    public int numberType;
    public List<Long> addressList = new ArrayList<Long>();
    public boolean isQuickSearch;
    
    public SearchResult(int type,List<Long> list,boolean isQuick){
        numberType = type;
        addressList = list;
        isQuickSearch = isQuick;
    }
    
}
