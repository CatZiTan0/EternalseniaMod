package com.zitan.eternalseniahook;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import sun.misc.Unsafe;

public class HookInit implements IXposedHookLoadPackage {

    Unsafe unsafe;
    Activity theActivity;
    static SearchResult lastResult;
    SharedPreferences modulePreferences;
    HashMap<Long,Integer> frozenAddress = new HashMap<Long,Integer>();

	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        // 获取Unsafe实例
	    try {
	        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
	        theUnsafe.setAccessible(true);
            unsafe = (Unsafe)theUnsafe.get(null);
	    } catch (Exception e) {}

        XposedHelpers.findAndHookMethod(Activity.class, "setContentView", View.class, new XC_MethodHook(){
                protected void afterHookedMethod(MethodHookParam activityParams)throws Throwable {
                    // 获取宿主activity实例
                    theActivity = (Activity) activityParams.thisObject;

                    // 冻结线程，循环修改值实现冻结指定地址数据
                    Thread frozenThread = new Thread(new Runnable(){
                            @Override
                            public void run() {
                                while (true) {
                                    Set<Long> keySet = frozenAddress.keySet();
                                    if (keySet.isEmpty()) {
                                        continue;
                                    }

                                    Iterator<Long> keyIterator = keySet.iterator();
                                    while (keyIterator.hasNext()) {
                                        long targetAddress = keyIterator.next();
                                        int targetValue = frozenAddress.get(targetAddress);
                                        unsafe.putInt(targetAddress, targetValue);
                                    }
                                }

                            }
                        });
                    frozenThread.start();

                    // 获取ContentView
                    ViewGroup decorView = (ViewGroup) activityParams.args[0];
                    modulePreferences = theActivity.getSharedPreferences("HookModulePreferences", Context.MODE_PRIVATE);

                    // 获取宿主图标用来绘制弹窗
                    Drawable iconDrowable = theActivity.getPackageManager().getApplicationIcon(theActivity.getApplicationInfo());
                    ImageView iconView = new ImageView(theActivity);
                    iconView.setImageDrawable(iconDrowable);
                    FrameLayout.LayoutParams layoutParam = new FrameLayout.LayoutParams(200, 200);
                    layoutParam.leftMargin = 0;
                    layoutParam.topMargin = 0;
                    iconView.setLayoutParams(layoutParam);

                    decorView.addView(iconView);
                    // 设置拖动/点击事件
                    iconView.setOnTouchListener(new View.OnTouchListener() {
                            private int initialX, initialY;
                            private float lastTouchX, lastTouchY;
                            private int touchSlop = ViewConfiguration.get(theActivity).getScaledTouchSlop();
                            private boolean isDragging = false;

                            @Override
                            public boolean onTouch(View v, MotionEvent event) {
                                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) v.getLayoutParams();

                                switch (event.getActionMasked()) {
                                    case MotionEvent.ACTION_DOWN:
                                        lastTouchX = event.getRawX();
                                        lastTouchY = event.getRawY();
                                        initialX = params.leftMargin;
                                        initialY = params.topMargin;
                                        return true;

                                    case MotionEvent.ACTION_MOVE:
                                        float dx = event.getRawX() - lastTouchX;
                                        float dy = event.getRawY() - lastTouchY;

                                        // 检测是否达到滑动阈值
                                        if (!isDragging && (dx * dx + dy * dy > touchSlop * touchSlop)) {
                                            isDragging = true;
                                        }

                                        if (isDragging) {
                                            params.leftMargin = (int) (initialX + (event.getRawX() - lastTouchX));
                                            params.topMargin = (int) (initialY + (event.getRawY() - lastTouchY));
                                            v.setLayoutParams(params);
                                        }
                                        return true;

                                    case MotionEvent.ACTION_UP:
                                        if (!isDragging) {
                                            // 显示点击对话框
                                            AlertDialog.Builder builder = new AlertDialog.Builder(theActivity);
                                            builder.setTitle("修改菜单");
                                            // 菜单
                                            builder.setItems(new CharSequence[]{"查找内存(暴力搜索)","查找内存(根据物品ID)","二次搜索","武器修改(刷蓝宝石)","展示搜索数据","清除记录的数据","修改指定地址数据","查看内存数据","修改物品","设置","使用说明"}, new DialogInterface.OnClickListener(){
                                                    @Override
                                                    public void onClick(DialogInterface dialog, int which) {
                                                        if (which == 0) {
                                                            checkMemoryByQuick();
                                                        } else if (which == 1) {
                                                            checkMemoryByID();
                                                        } else if (which == 2) {
                                                            searchAgain();
                                                        } else if (which == 3) {
                                                            changeWeapon();
                                                        } else if (which == 4) {
                                                            showSearchResult();
                                                        } else if (which == 5) {
                                                            clearResult();
                                                        } else if (which == 6) {
                                                            resetMemory();
                                                        } else if (which == 7) {
                                                            showMemoryInfo();
                                                        } else if (which == 8) {
                                                            modifyItems();
                                                        } else if (which == 9) {
                                                            set();
                                                        } else if (which == 10) {
                                                            Toast.makeText(theActivity, "暂时没有做嘞(ﾟДﾟ≡ﾟдﾟ)!?", Toast.LENGTH_SHORT).show();
                                                        }
                                                    }

                                                });
                                            builder.setNegativeButton("取消", null);
                                            builder.create().show();
                                        }
                                        isDragging = false;
                                        return true;
                                }
                                return false;
                            }
                        });
                }
            });
	}

    private void set() {
        final SharedPreferences.Editor editor = modulePreferences.edit();
        AlertDialog.Builder builder = new AlertDialog.Builder(theActivity);
        builder.setTitle("设置");
        LinearLayout linear = new LinearLayout(theActivity);
        linear.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 150);
        LinearLayout set1 = new LinearLayout(theActivity);
        set1.setLayoutParams(params);
        set1.setOrientation(LinearLayout.HORIZONTAL);
        TextView textView = new TextView(theActivity);
        textView.setText("关闭查找内存功能弹窗提示");
        LinearLayout.LayoutParams textViewParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT);
        textViewParams.weight = 1;
        textView.setLayoutParams(textViewParams);
        textView.setGravity(Gravity.CENTER);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        final CheckBox checkBox = new CheckBox(theActivity);
        checkBox.setLayoutParams(new LinearLayout.LayoutParams(150, 150));
        checkBox.setChecked(modulePreferences.getBoolean("closeToast", false));
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    editor.putBoolean("closeToast", isChecked);
                    editor.apply();
                }
            });
        set1.setClickable(true);
        set1.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    checkBox.setChecked(!checkBox.isChecked());
                    editor.putBoolean("closeToast", checkBox.isChecked());
                    editor.apply();
                }
            });
        set1.addView(textView);
        set1.addView(checkBox);
        linear.addView(set1);

        LinearLayout set2 = new LinearLayout(theActivity);
        set2.setLayoutParams(params);
        set2.setClickable(true);
        TextView textView2 = new TextView(theActivity);
        textView2.setText("设置查看内存范围");
        textView2.setGravity(Gravity.CENTER);
        textView2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        textView2.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        set2.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    AlertDialog.Builder set2builder = new AlertDialog.Builder(theActivity);
                    set2builder.setTitle("设置查看内存范围");
                    final EditText upwardText = new EditText(theActivity);
                    final EditText downwardText = new EditText(theActivity);
                    upwardText.setHint("向上显示地址数");
                    downwardText.setHint("向下显示地址数");
                    LinearLayout set2linear = new LinearLayout(theActivity);
                    set2linear.setOrientation(LinearLayout.VERTICAL);
                    upwardText.setText(Integer.toString(modulePreferences.getInt("upwardRange", 20)));
                    downwardText.setText(Integer.toString(modulePreferences.getInt("downwardRange", 20)));
                    set2linear.addView(upwardText);
                    set2linear.addView(downwardText);
                    upwardText.setInputType(InputType.TYPE_CLASS_NUMBER);
                    downwardText.setInputType(InputType.TYPE_CLASS_NUMBER);
                    set2builder.setPositiveButton("确定", new DialogInterface.OnClickListener(){
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                editor.putInt("upwardRange", upwardText.getText().toString().equals("") ?0: Integer.parseInt(upwardText.getText().toString()));
                                editor.putInt("downwardRange", downwardText.getText().toString().equals("") ?0: Integer.parseInt(downwardText.getText().toString()));
                                editor.apply();
                            }
                        });
                    set2builder.setView(set2linear);
                    set2builder.create().show();
                }
            });
        set2.addView(textView2);
        linear.addView(set2);

        LinearLayout set3 = new LinearLayout(theActivity);
        set3.setLayoutParams(params);
        set3.setClickable(true);
        TextView textView3 = new TextView(theActivity);
        textView3.setText("危险功能--数值冻结");
        textView3.setGravity(Gravity.CENTER);
        textView3.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        textView3.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        set3.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    AlertDialog.Builder set3builder = new AlertDialog.Builder(theActivity);
                    set3builder.setTitle("数值冻结");
                    final EditText addressText = new EditText(theActivity);
                    final EditText valueText = new EditText(theActivity);
                    addressText.setHint("想要冻结的地址");
                    valueText.setHint("想要冻结的数值");
                    LinearLayout set3linear = new LinearLayout(theActivity);
                    set3linear.setOrientation(LinearLayout.VERTICAL);
                    Button button2 = new Button(theActivity);
                    button2.setText("查看已冻结数据");
                    button2.setOnClickListener(new View.OnClickListener(){
                            @Override
                            public void onClick(View v) {
                                AlertDialog.Builder checkBuilder = new AlertDialog.Builder(theActivity);
                                checkBuilder.setTitle("冻结地址表");
                                TextView frozenTextView = new TextView(theActivity);
                                StringBuilder frozenListStringBuilder = new StringBuilder();
                                Set<Long> keySet = frozenAddress.keySet();
                                if (keySet.isEmpty()) {
                                    frozenTextView.setText("无数据");
                                }

                                Iterator<Long> keyIterator = keySet.iterator();
                                while (keyIterator.hasNext()) {
                                    long targetAddress = keyIterator.next();
                                    int targetValue = frozenAddress.get(targetAddress);
                                    frozenListStringBuilder.append(Long.toString(targetAddress) + "(0x" + Long.toHexString(targetAddress) + ") :" + Integer.toString(targetValue) + "\n");
                                }
                                frozenTextView.setText(frozenListStringBuilder.toString());
                                frozenTextView.setTextIsSelectable(true);
                                checkBuilder.setView(frozenTextView);
                                checkBuilder.setNegativeButton("取消", null);
                                checkBuilder.create().show();
                            }
                        });
                    set3linear.addView(button2);
                    set3linear.addView(addressText);
                    Button button = new Button(theActivity);
                    button.setText("取消冻结目标地址");
                    button.setOnClickListener(new View.OnClickListener(){
                            @Override
                            public void onClick(View v) {
                                if (addressText.getText().toString().equals("")) {
                                    return;
                                }

                                long targetAddress = Long.parseLong(addressText.getText().toString());
                                frozenAddress.remove(targetAddress);
                                Toast.makeText(theActivity, "成功取消冻结地址 :" + Long.toString(targetAddress), Toast.LENGTH_SHORT).show();
                            }
                        });


                    set3linear.addView(button);
                    set3linear.addView(valueText);
                    addressText.setInputType(InputType.TYPE_CLASS_NUMBER);
                    valueText.setInputType(InputType.TYPE_CLASS_NUMBER);
                    set3builder.setPositiveButton("冻结", new DialogInterface.OnClickListener(){
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (addressText.getText().toString().equals("")) {
                                    return;
                                }
                                if (valueText.getText().toString().equals("")) {
                                    return;
                                }
                                long inputAddress = Long.parseLong(addressText.getText().toString());
                                if (MemUtils.isAType(inputAddress)) {
                                    frozenAddress.put(inputAddress, Integer.parseInt(valueText.getText().toString()));
                                    Toast.makeText(theActivity, "提交冻结数据成功", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(theActivity, "非A类型值，暂不支持修改", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    set3builder.setView(set3linear);
                    set3builder.create().show();
                }
            });
        set3.addView(textView3);
        linear.addView(set3);

        builder.setView(linear);
        builder.setPositiveButton("确定", null);
        builder.create().show();
    }

    private void modifyItems() {
        AlertDialog.Builder builder = new AlertDialog.Builder(theActivity);
        builder.setTitle("修改物品");
        LinearLayout linearLayout = new LinearLayout(theActivity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        final EditText editText = new EditText(theActivity);
        editText.setHint("输入物品数量地址");
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 150));
        Button getValueButton = new Button(theActivity);
        getValueButton.setText("获取物品ID");
        getValueButton.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 150));
        getValueButton.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    if (editText.getText().toString().equals("")) {
                        return;
                    }
                    long inputAddress = Long.parseLong(editText.getText().toString());
                    if (unsafe.getInt(inputAddress - 4) != unsafe.getInt(inputAddress - 12)) {
                        Toast.makeText(theActivity, "输入的地址不是物品数量地址", Toast.LENGTH_SHORT).show();
                    } else if (MemUtils.isAType(inputAddress)) {
                        Toast.makeText(theActivity, "物品ID为:" + unsafe.getInt(inputAddress - 4), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(theActivity, "非A类型值，暂不支持查找", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        final EditText changeText = new EditText(theActivity);
        changeText.setHint("输入想要修改到的物品ID");
        changeText.setInputType(InputType.TYPE_CLASS_NUMBER);
        changeText.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 150));
        Button changeValueButton = new Button(theActivity);
        changeValueButton.setText("修改物品ID");
        changeValueButton.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 150));
        changeValueButton.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    if (changeText.getText().toString().equals("")) {
                        return;
                    }
                    long inputAddress = Long.parseLong(editText.getText().toString());
                    if (MemUtils.isAType(inputAddress)) {
                        int newValue = Integer.parseInt(changeText.getText().toString());
                        unsafe.putInt(inputAddress - 4, newValue);
                        unsafe.putInt(inputAddress - 12, newValue);
                        Toast.makeText(theActivity, "修改成功，已将物品ID修改为 :" + unsafe.getInt(inputAddress - 4), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(theActivity, "非A类型值，暂不支持修改", Toast.LENGTH_SHORT).show();
                    }
                }
            });

        linearLayout.addView(editText);
        linearLayout.addView(getValueButton);
        linearLayout.addView(changeText);
        linearLayout.addView(changeValueButton);
        builder.setView(linearLayout);
        builder.setNegativeButton("关闭", null);
        builder.create().show();
    }

    private void showMemoryInfo() {
        AlertDialog.Builder checkbuilder = new AlertDialog.Builder(theActivity);
        checkbuilder.setTitle("查看内存数据");
        final EditText editText = new EditText(theActivity);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setHint("输入要查看的内存地址");
        checkbuilder.setView(editText);
        checkbuilder.setPositiveButton("查看", new DialogInterface.OnClickListener(){
                @Override
                public void onClick(DialogInterface dialog, int which) {

                    if (editText.getText().toString().equals("")) {
                        return;
                    }
                    long targetAddress = Long.parseLong(editText.getText().toString());
                    if (MemUtils.isAType(targetAddress)) {
                        long pointerAddress;
                        int upwardRange = modulePreferences.getInt("upwardRange", 20);
                        int downwardRange = modulePreferences.getInt("downwardRange", 20);

                        AlertDialog.Builder infobuilder = new AlertDialog.Builder(theActivity);
                        infobuilder.setTitle("内存信息");
                        final ArrayList<String> adapterData = new ArrayList<String>();
                        int i = 0;
                        for (pointerAddress = targetAddress - upwardRange * 4;pointerAddress <= targetAddress + downwardRange * 4;pointerAddress = pointerAddress + 4) {
                            adapterData.add(Long.toString(pointerAddress) + "(0x" + Long.toHexString(pointerAddress) + "):" + unsafe.getInt(pointerAddress));
                            i++;
                        }
                        ListView listView = new ListView(theActivity);
                        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(theActivity, android.R.layout.simple_list_item_1, adapterData);

                        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener(){
                                @Override
                                public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                                    String addressString = ((TextView)view).getText().toString().split("\\(")[0];
                                    // 获取剪切板管理器
                                    ClipboardManager clipboard = (ClipboardManager) theActivity.getSystemService(Context.CLIPBOARD_SERVICE);
                                    // 创建一个剪切板数据对象
                                    ClipData clip = ClipData.newPlainText("label", addressString);
                                    // 将数据写入剪切板
                                    clipboard.setPrimaryClip(clip);
                                    Toast.makeText(theActivity, "地址已复制到剪切板", Toast.LENGTH_SHORT).show();
                                    return true;
                                }
                            });
                        listView.setOnItemClickListener(new AdapterView.OnItemClickListener(){
                                @Override
                                public void onItemClick(final AdapterView<?> parent, final View view, final int position, long id) {

                                    final String addressString = ((TextView)view).getText().toString();
                                    final long address = Long.parseLong(addressString.split("\\(")[0]);
                                    AlertDialog.Builder builder = new AlertDialog.Builder(theActivity);
                                    builder.setTitle("修改数据");
                                    LinearLayout linearLayout = new LinearLayout(theActivity);
                                    linearLayout.setOrientation(LinearLayout.VERTICAL);
                                    TextView addressText = new TextView(theActivity);
                                    addressText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                                    addressText.setText(Long.toString(address));
                                    addressText.setGravity(Gravity.CENTER_VERTICAL);
                                    addressText.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 150));
                                    Button getValueButton = new Button(theActivity);
                                    getValueButton.setText("获取值");
                                    getValueButton.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 150));
                                    getValueButton.setOnClickListener(new View.OnClickListener(){
                                            @Override
                                            public void onClick(View v) {
                                                Toast.makeText(theActivity, "值为:" + unsafe.getInt(address), Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                    final EditText changeText = new EditText(theActivity);
                                    changeText.setHint("输入值");
                                    changeText.setInputType(InputType.TYPE_CLASS_NUMBER);
                                    changeText.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 150));
                                    Button changeValueButton = new Button(theActivity);
                                    changeValueButton.setText("修改值");
                                    changeValueButton.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 150));

                                    changeValueButton.setOnClickListener(new View.OnClickListener(){
                                            @Override
                                            public void onClick(View v) {
                                                if (changeText.getText().toString().equals("")) {
                                                    return;
                                                }
                                                int newValue = Integer.parseInt(changeText.getText().toString());
                                                unsafe.putInt(address, newValue);
                                                final TextView newView = new TextView(theActivity);
                                                newView.setText(Long.toString(address) + "(0x" + Long.toHexString(address) + "):" + unsafe.getInt(address));
                                                adapterData.set(position, (addressString.split(":")[0] + ":" + unsafe.getInt(address)));
                                                adapter.notifyDataSetChanged();
                                                Toast.makeText(theActivity, "修改成功，新值为:" + unsafe.getInt(address), Toast.LENGTH_SHORT).show();
                                            }
                                        });


                                    linearLayout.addView(addressText);
                                    linearLayout.addView(getValueButton);
                                    linearLayout.addView(changeText);
                                    linearLayout.addView(changeValueButton);

                                    builder.setView(linearLayout);
                                    builder.setNegativeButton("关闭", null);
                                    builder.create().show();
                                }
                            });
                        listView.setAdapter(adapter);

                        infobuilder.setView(listView);
                        listView.setSelection(modulePreferences.getInt("upwardRange", 20));
                        infobuilder.setNegativeButton("取消", null);
                        infobuilder.create().show();
                    } else {
                        Toast.makeText(theActivity, "非A类型内存数据，暂不支持查看", Toast.LENGTH_LONG).show();
                    }
                }
            });
        checkbuilder.create().show();
    }

    private void resetMemory() {
        AlertDialog.Builder builder = new AlertDialog.Builder(theActivity);
        builder.setTitle("修改数据");
        LinearLayout linearLayout = new LinearLayout(theActivity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        final EditText editText = new EditText(theActivity);
        editText.setHint("输入地址");
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 150));
        Button getValueButton = new Button(theActivity);
        getValueButton.setText("获取值");
        getValueButton.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 150));
        getValueButton.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    if (editText.getText().toString().equals("")) {
                        return;
                    }
                    long inputAddress = Long.parseLong(editText.getText().toString());
                    if (MemUtils.isAType(inputAddress)) {
                        Toast.makeText(theActivity, "值为:" + unsafe.getInt(inputAddress), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(theActivity, "非A类型值，暂不支持查找", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        final EditText changeText = new EditText(theActivity);
        changeText.setHint("输入值");
        changeText.setInputType(InputType.TYPE_CLASS_NUMBER);
        changeText.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 150));
        Button changeValueButton = new Button(theActivity);
        changeValueButton.setText("修改值");
        changeValueButton.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 150));
        changeValueButton.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    if (changeText.getText().toString().equals("")) {
                        return;
                    }
                    long inputAddress = Long.parseLong(editText.getText().toString());
                    if (MemUtils.isAType(inputAddress)) {
                        int newValue = Integer.parseInt(changeText.getText().toString());
                        unsafe.putInt(inputAddress, newValue);
                        Toast.makeText(theActivity, "修改成功，新值为:" + unsafe.getInt(inputAddress), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(theActivity, "非A类型值，暂不支持修改", Toast.LENGTH_SHORT).show();
                    }
                }
            });

        linearLayout.addView(editText);
        linearLayout.addView(getValueButton);
        linearLayout.addView(changeText);
        linearLayout.addView(changeValueButton);
        builder.setView(linearLayout);
        builder.setNegativeButton("关闭", null);
        builder.create().show();
    }

    private void clearResult() {
        lastResult = null;
        Toast.makeText(theActivity, "清除成功", Toast.LENGTH_SHORT).show();
    }

    private void showSearchResult() {
        if (lastResult == null) {
            Toast.makeText(theActivity, "无数据，请先使用查找内存", Toast.LENGTH_SHORT).show();
            return;
        }

        if (lastResult.addressList.isEmpty()) {
            Toast.makeText(theActivity, "未搜索到数据，请重试", Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(theActivity);
        builder.setTitle("搜索结果(长按复制地址)");
        final ArrayList<String> adapterData = new ArrayList<String>();
        for (int i = 0;i < lastResult.addressList.size();i ++) {
            adapterData.add(lastResult.addressList.get(i) + "(0x" + Long.toHexString(lastResult.addressList.get(i)) + "):" + unsafe.getInt(lastResult.addressList.get(i)));
        }
        ListView listView = new ListView(theActivity);
        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(theActivity, android.R.layout.simple_list_item_1, adapterData);
        listView.setAdapter(adapter);
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener(){
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                    String addressString = ((TextView)view).getText().toString().split("\\(")[0];
                    // 获取剪切板管理器
                    ClipboardManager clipboard = (ClipboardManager) theActivity.getSystemService(Context.CLIPBOARD_SERVICE);
                    // 创建一个剪切板数据对象
                    ClipData clip = ClipData.newPlainText("label", addressString);
                    // 将数据写入剪切板
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(theActivity, "地址已复制到剪切板", Toast.LENGTH_SHORT).show();
                    return true;
                }
            });
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener(){
                @Override
                public void onItemClick(AdapterView<?> parent, final View view, final int position, long id) {
                    final String addressString = ((TextView)view).getText().toString();
                    final long address = Long.parseLong(addressString.split("\\(")[0]);
                    AlertDialog.Builder builder = new AlertDialog.Builder(theActivity);
                    builder.setTitle("修改数据");
                    LinearLayout linearLayout = new LinearLayout(theActivity);
                    linearLayout.setOrientation(LinearLayout.VERTICAL);
                    TextView addressText = new TextView(theActivity);
                    addressText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                    addressText.setText(Long.toString(address));
                    addressText.setGravity(Gravity.CENTER_VERTICAL);
                    addressText.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 150));
                    Button getValueButton = new Button(theActivity);
                    getValueButton.setText("获取值");
                    getValueButton.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 150));
                    getValueButton.setOnClickListener(new View.OnClickListener(){
                            @Override
                            public void onClick(View v) {
                                Toast.makeText(theActivity, "值为:" + unsafe.getInt(address), Toast.LENGTH_SHORT).show();
                            }
                        });
                    final EditText changeText = new EditText(theActivity);
                    changeText.setHint("输入值");
                    changeText.setInputType(InputType.TYPE_CLASS_NUMBER);
                    changeText.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 150));
                    Button changeValueButton = new Button(theActivity);
                    changeValueButton.setText("修改值");
                    changeValueButton.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 150));
                    changeValueButton.setOnClickListener(new View.OnClickListener(){
                            @Override
                            public void onClick(View v) {
                                if (changeText.getText().toString().equals("")) {
                                    return;
                                }
                                int newValue = Integer.parseInt(changeText.getText().toString());
                                unsafe.putInt(address, newValue);
                                adapterData.set(position, (addressString.split(":")[0] + ":" + newValue));
                                adapter.notifyDataSetChanged();
                                Toast.makeText(theActivity, "修改成功，新值为:" + unsafe.getInt(address), Toast.LENGTH_SHORT).show();
                            }
                        });

                    linearLayout.addView(addressText);
                    linearLayout.addView(getValueButton);
                    linearLayout.addView(changeText);
                    linearLayout.addView(changeValueButton);
                    builder.setView(linearLayout);
                    builder.setNegativeButton("关闭", null);
                    builder.create().show();
                }
            });
        builder.setView(listView);
        builder.setNegativeButton("取消", null);
        builder.create().show();

    }

    private void searchAgain() {
        if (lastResult == null) {
            Toast.makeText(theActivity, "无数据，请先使用查找内存", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(theActivity);
        builder.setTitle("二次搜索");
        final EditText editText = new EditText(theActivity);
        editText.setHint("输入搜索数值");
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        builder.setView(editText);

        builder.setPositiveButton("搜索", new DialogInterface.OnClickListener(){
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (editText.getText().toString().equals("")) {
                        return;
                    }
                    List<Long> targetAddresses = new ArrayList<Long>();
                    if (lastResult.numberType == SearchResult.byteType) {
                        byte searchNumber = Byte.parseByte(editText.getText().toString());
                        for (long targetAddress : lastResult.addressList) {
                            if (unsafe.getByte(targetAddress) == searchNumber) {
                                targetAddresses.add(targetAddress);
                            }
                        }
                    } else if (lastResult.numberType == SearchResult.intType) {
                        int searchNumber = Integer.parseInt(editText.getText().toString());
                        for (long targetAddress : lastResult.addressList) {
                            if (unsafe.getInt(targetAddress) == searchNumber) {
                                targetAddresses.add(targetAddress);
                            }
                        }
                    } else if (lastResult.numberType == SearchResult.longType) {
                        long searchNumber = Long.parseLong(editText.getText().toString());
                        for (long targetAddress : lastResult.addressList) {
                            if (unsafe.getLong(targetAddress) == searchNumber) {
                                targetAddresses.add(targetAddress);
                            }
                        }
                    } else if (lastResult.numberType == SearchResult.noType) {
                        int searchNumber = Integer.parseInt(editText.getText().toString());
                        for (long targetAddress : lastResult.addressList) {
                            if (unsafe.getInt(targetAddress) == searchNumber) {
                                targetAddresses.add(targetAddress);
                            }
                        }
                    }

                    if (targetAddresses.isEmpty()) {
                        Toast.makeText(theActivity, "搜索不到数据", Toast.LENGTH_SHORT).show();
                        lastResult = null;
                        return;
                    }
                    lastResult.addressList = targetAddresses;
                    Toast.makeText(theActivity, "搜索结束，搜索到:" + lastResult.addressList.size() + "个数据", Toast.LENGTH_LONG).show();
                }
            });
        builder.create().show();

    }


    List<Long> weaponList = new ArrayList<Long>();
    private void changeWeapon() {
        AlertDialog.Builder builder = new AlertDialog.Builder(theActivity);
        builder.setTitle("修改武器");
        LinearLayout linear = new LinearLayout(theActivity);
        linear.setOrientation(LinearLayout.VERTICAL);
        linear.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        Button button1 = new Button(theActivity);
        button1.setText("获取一把红莲剑然后点我");
        button1.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    if (!weaponList.isEmpty()) {
                        weaponList = new ArrayList<Long>();
                    }

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
                                long pointerAddress = 0;
                                for (pointerAddress = startAddress;pointerAddress < endAddress;pointerAddress = pointerAddress + 4) {
                                    int getValue = unsafe.getInt(pointerAddress);

                                    if (getValue == 2001) {
                                        weaponList.add(pointerAddress);
                                    }
                                }

                            }
                        }

                        if (weaponList.isEmpty()) {
                            Toast.makeText(theActivity, "未搜索到数值，请尝试重新搜索", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(theActivity, "搜索到:" + weaponList.size() + "个数据，把红莲剑升到一阶后使用下面的功能", Toast.LENGTH_LONG).show();
                        }
                    } catch (Exception e) {
                        XposedBridge.log(e);
                    }
                }
            });
        Button button2 = new Button(theActivity);
        button2.setText("升到一阶后点我");
        button2.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    if (weaponList.isEmpty()) {
                        Toast.makeText(theActivity, "武器地址列表为空，确认一下是否记录过数据", Toast.LENGTH_LONG).show();
                        return;
                    }

                    for (long address : weaponList) {
                        int getCode = unsafe.getInt(address);
                        if (getCode == 2101) {
                            unsafe.putInt(address, 2505);
                            unsafe.putInt(address + 4, 100);
                            Toast.makeText(theActivity, "修改了一把红莲剑，分解它获取蓝宝石", Toast.LENGTH_LONG).show();
                        }
                    }
                }
            });

        linear.addView(button1);
        linear.addView(button2);
        builder.setView(linear);
        builder.setPositiveButton("取消", null);
        builder.create().show();
    }

    private void checkMemoryByID() {
        if (!modulePreferences.getBoolean("closeToast", false)) {
            Toast.makeText(theActivity, "此模式会清空历史记录，如果想要过滤数据请使用二次搜索", Toast.LENGTH_LONG).show();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(theActivity);
        builder.setTitle("根据ID搜索");
        final EditText editText = new EditText(theActivity);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setHint("输入想要搜索数值");
        final EditText idEditText = new EditText(theActivity);
        idEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
        idEditText.setHint("输入物品ID(不了解物品ID请使用暴力搜索)");
        LinearLayout linear = new LinearLayout(theActivity);
        linear.setOrientation(LinearLayout.VERTICAL);
        linear.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        linear.addView(idEditText);
        linear.addView(editText);
        builder.setView(linear);
        builder.setPositiveButton("搜索", new DialogInterface.OnClickListener(){
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (editText.getText().toString().equals("")) {
                        return;
                    }
                    if (idEditText.getText().toString().equals("")) {
                        return;
                    }
                    Toast.makeText(theActivity, "正在搜索，请耐心等待", Toast.LENGTH_LONG).show();
                    try {
                        File mapsFile = new File("/proc/self/maps");
                        BufferedReader mapsReader = new BufferedReader(new FileReader(mapsFile));
                        String line;
                        List<Long> targetAddresses = new ArrayList<Long>();
                        while ((line = mapsReader.readLine()) != null) {
                            String accessAuthority = line.split(" ")[1];
                            if (accessAuthority.contains("r") && accessAuthority.contains("w") && accessAuthority.contains("p") && line.split(" ").length < 7) {
                                String addressString = line.split(" ")[0];
                                long startAddress = Long.parseLong(addressString.split("-")[0], 16);
                                long endAddress = Long.parseLong(addressString.split("-")[1], 16);
                                long pointerAddress = 0;
                                int IDNumber = Integer.parseInt(idEditText.getText().toString());
                                int searchNumber = Integer.parseInt(editText.getText().toString());
                                for (pointerAddress = startAddress;pointerAddress < (endAddress - 16);pointerAddress = pointerAddress + 4) {
                                    int IDValue = unsafe.getInt(pointerAddress);

                                    if (IDValue == IDNumber && IDNumber == unsafe.getInt(pointerAddress + 8) && searchNumber == unsafe.getInt(pointerAddress + 12)) {
                                        targetAddresses.add(pointerAddress + 12);
                                    }
                                }

                            }
                        }

                        if (targetAddresses.isEmpty()) {
                            Toast.makeText(theActivity, "未搜索到数值，请尝试重新搜索", Toast.LENGTH_LONG).show();
                        } else {
                            lastResult = new SearchResult(SearchResult.noType, targetAddresses, true);
                            if (targetAddresses.size() > 10) {
                                Toast.makeText(theActivity, "数据量过大，建议二次搜索", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(theActivity, "搜索结束，搜索到:" + lastResult.addressList.size() + "个数据", Toast.LENGTH_SHORT).show();
                            }
                        }
                    } catch (Exception e) {
                        XposedBridge.log(e);
                    }
                }
            });
        builder.create().show();
    }

    private void checkMemoryByQuick() {
        if (!modulePreferences.getBoolean("closeToast", false)) {
            Toast.makeText(theActivity, "此模式会清空历史记录，如果想要过滤数据请使用二次搜索", Toast.LENGTH_LONG).show();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(theActivity);
        builder.setTitle("暴力搜索");
        final EditText editText = new EditText(theActivity);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setHint("输入想要搜索数值");
        builder.setView(editText);
        builder.setPositiveButton("搜索", new DialogInterface.OnClickListener(){
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (editText.getText().toString().equals("")) {
                        return;
                    }
                    Toast.makeText(theActivity, "正在搜索，请耐心等待", Toast.LENGTH_LONG).show();
                    try {
                        File mapsFile = new File("/proc/self/maps");
                        BufferedReader mapsReader = new BufferedReader(new FileReader(mapsFile));
                        String line;
                        List<Long> targetAddresses = new ArrayList<Long>();
                        while ((line = mapsReader.readLine()) != null) {
                            String accessAuthority = line.split(" ")[1];
                            if (accessAuthority.contains("r") && accessAuthority.contains("w") && accessAuthority.contains("p") && line.split(" ").length < 7) {
                                String addressString = line.split(" ")[0];
                                long startAddress = Long.parseLong(addressString.split("-")[0], 16);
                                long endAddress = Long.parseLong(addressString.split("-")[1], 16);
                                long pointerAddress = 0;
                                int searchNumber = Integer.parseInt(editText.getText().toString());
                                for (pointerAddress = startAddress;pointerAddress < endAddress;pointerAddress = pointerAddress + 4) {
                                    int getValue = unsafe.getInt(pointerAddress);

                                    if (getValue == searchNumber) {
                                        targetAddresses.add(pointerAddress);
                                    }
                                }

                            }
                        }

                        if (targetAddresses.isEmpty()) {
                            Toast.makeText(theActivity, "未搜索到数值，请尝试重新搜索", Toast.LENGTH_LONG).show();
                        } else {
                            lastResult = new SearchResult(SearchResult.noType, targetAddresses, true);
                            if (targetAddresses.size() > 10) {
                                Toast.makeText(theActivity, "数据量过大，建议二次搜索", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(theActivity, "搜索结束，搜索到:" + lastResult.addressList.size() + "个数据", Toast.LENGTH_SHORT).show();
                            }
                        }
                    } catch (Exception e) {
                        XposedBridge.log(e);
                    }
                }
            });
        builder.create().show();
    }
}
