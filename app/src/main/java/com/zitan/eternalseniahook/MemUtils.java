package com.zitan.eternalseniahook;

/**
 * @Author 紫檀
 * @Date 2025/04/03 16:05
 */
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ArrayList;

public class MemUtils {

    HashMap<Long,Long> memTypeA = new HashMap<Long,Long>();

    public MemUtils() {
        try {
            File mapsFile = new File("/proc/self/maps");
            BufferedReader mapsReader = new BufferedReader(new FileReader(mapsFile));
            String line;
            while ((line = mapsReader.readLine()) != null) {
                String accessAuthority = line.split(" ")[1];
                if (accessAuthority.contains("r") && accessAuthority.contains("w") && accessAuthority.contains("p") && line.split(" ").length < 7) {
                    String addressString = line.split(" ")[0];
                    long startAddress = Long.parseLong(addressString.split("-")[0], 16);
                    long endAddress = Long.parseLong(addressString.split("-")[1], 16);
                    memTypeA.put(startAddress, endAddress);
                }
            }

        } catch (Exception e) {
            return;
        }
    }
    
    public static ArrayList<Long> getAllATypeAddress(){
        ArrayList<Long> memTypeAList = new ArrayList<Long>();
        try {
            File mapsFile = new File("/proc/self/maps");
            BufferedReader mapsReader = new BufferedReader(new FileReader(mapsFile));
            String line;
            while ((line = mapsReader.readLine()) != null) {
                String accessAuthority = line.split(" ")[1];
                if (accessAuthority.contains("r") && accessAuthority.contains("w") && accessAuthority.contains("p") && line.split(" ").length < 7) {
                    String addressString = line.split(" ")[0];
                    long startAddress = Long.parseLong(addressString.split("-")[0], 16);
                    long endAddress = Long.parseLong(addressString.split("-")[1], 16);
                    for(long pointerAddress = startAddress;pointerAddress < endAddress;pointerAddress = pointerAddress + 4){
                        memTypeAList.add(pointerAddress);
                    }
                }
            }
        } catch (Exception e) {}
        return memTypeAList;
    }

    // 实例方法，构造函数中读取一次maps，后续全部使用读取的maps中的数据
    public boolean isAMemoryType(long address) {
        Iterator<Map.Entry<Long,Long>> iterator = memTypeA.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long,Long> entry = iterator.next();
            if (address >= entry.getKey() && address <= entry.getValue()) {
                return true;
            }
        }
        return false;
    }

    // 静态方法，每次调用都读取一次maps，保证数据准确
    public static boolean isAType(long address) {
        try {
            File mapsFile = new File("/proc/self/maps");
            BufferedReader mapsReader = new BufferedReader(new FileReader(mapsFile));
            String line;
            boolean isAType = false;
            while ((line = mapsReader.readLine()) != null) {
                String accessAuthority = line.split(" ")[1];
                if (accessAuthority.contains("r") && accessAuthority.contains("w") && accessAuthority.contains("p") && line.split(" ").length < 7) {
                    String addressString = line.split(" ")[0];
                    long startAddress = Long.parseLong(addressString.split("-")[0], 16);
                    long endAddress = Long.parseLong(addressString.split("-")[1], 16);
                    if (address >= startAddress && address <= endAddress) {
                        isAType = true;
                    }
                }
            }

            return isAType;
        } catch (Exception e) {
            return false;
        }
    }
    
    public static boolean isAddressSafe(long address){
        try {
            File mapsFile = new File("/proc/self/maps");
            BufferedReader mapsReader = new BufferedReader(new FileReader(mapsFile));
            String line;
            while ((line = mapsReader.readLine()) != null) {
                String addressString = line.split(" ")[0];
                long startAddress = Long.parseLong(addressString.split("-")[0], 16);
                long endAddress = Long.parseLong(addressString.split("-")[1], 16);
                if (address >= startAddress && address <= endAddress) {
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

}
