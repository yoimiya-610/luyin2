package cn.luyin.player;

import java.util.Collection;
import java.util.Locale;

/** File names cannot escape their recording directory. */
public final class NameRules {
    public static String clean(String value) {
        String name=value.trim();
        if(name.isEmpty()||name.equals(".")||name.equals("..")||name.length()>100
                ||name.matches("(?s).*[\\\\/:*?\"<>|\\p{Cntrl}].*"))
            throw new IllegalArgumentException("名称需为 1–100 个字符，不能包含换行或 / \\ : * ? \" < > | 等符号");
        return name;
    }
    public static int nextNumber(String day,int reserved,Collection<String> names) {
        int max=Math.max(0,reserved);
        for(String name:names) {
            String base=name.toLowerCase(Locale.ROOT).replaceFirst("\\.m4a$","");
            if(!base.startsWith(day+"-"))continue;
            try{max=Math.max(max,Integer.parseInt(base.substring(day.length()+1)));}catch(NumberFormatException ignored){}
        }
        if(max==Integer.MAX_VALUE)throw new IllegalStateException("当天录音编号已用尽");
        return max+1;
    }
    public static String withExtension(String title,String original) {
        String clean=clean(title);int dot=original.lastIndexOf('.');
        String ext=dot>=0?original.substring(dot):"";
        return !ext.isEmpty()&&!clean.toLowerCase(Locale.ROOT).endsWith(ext.toLowerCase(Locale.ROOT))?clean+ext:clean;
    }
    private NameRules(){}
}
