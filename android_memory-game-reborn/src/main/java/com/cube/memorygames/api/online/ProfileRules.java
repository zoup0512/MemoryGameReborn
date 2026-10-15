package com.cube.memorygames.api.online;

import java.util.Random;

/** Pure Java profile policy. This is format guidance, never a substitute for server moderation. */
public final class ProfileRules {
    private static final String[] FIRST={"清风","暖阳","星光","月色","晨露","云间","山林","秋叶","春雨","蓝天","溪边","晴空"};
    private static final String[] LAST={"小鹿","白兔","松鼠","海豚","熊猫","小狐","雪鸮","小猫","企鹅","水獭","小熊","考拉"};
    private ProfileRules() {}
    public static String inspiration(Random random) { return FIRST[random.nextInt(FIRST.length)]+LAST[random.nextInt(LAST.length)]; }
    public static String normalize(String name) {
        int start=0,end=name.length();
        // Only ordinary boundary spaces are stripped; forbidden whitespace must still be rejected.
        while(start<end && name.charAt(start)==' ')start++;
        while(end>start && name.charAt(end-1)==' ')end--;
        return java.text.Normalizer.normalize(name.substring(start,end),java.text.Normalizer.Form.NFC);
    }
    /** Null means omit nickname from PATCH, including unchanged historical values no longer valid for new names. */
    public static String nicknameChange(String original,String draft) {
        if(same(original,draft))return null;
        String normalized=normalize(draft);
        return same(original,normalized) ? null : normalized;
    }
    public static boolean validNickname(String name) {
        if(name==null) return false;
        name=normalize(name);
        int count=name.codePointCount(0,name.length());
        if(count<2 || count>16) return false;
        for(int i=0;i<name.length();) {
            int c=name.codePointAt(i), type=Character.getType(c);
            if(Character.isISOControl(c) || (c!=0x20 && (Character.isWhitespace(c) || Character.isSpaceChar(c)))
                    || type==Character.FORMAT || type==Character.UNASSIGNED || type==Character.SURROGATE
                    || type==Character.PRIVATE_USE || c==0x034f || c==0x2800
                    || (c>=0x115f && c<=0x1160) || c==0x3164 || c==0xffa0
                    || (c>=0xfe00 && c<=0xfe0f) || (c>=0xe0100 && c<=0xe01ef)) return false;
            i+=Character.charCount(c);
        }
        return true;
    }
    public static int avatarIndex(String value) {
        if(value==null || !value.matches("avatar_([1-9]|1[0-2])")) return 0;
        return Integer.parseInt(value.substring(7))-1;
    }
    public static boolean validAvatar(String value) { return value!=null && value.matches("avatar_([1-9]|1[0-2])"); }
    public static boolean coolingDown(long next,long now) { return next>now; }
    public static boolean same(String a,String b) { return a==null ? b==null : a.equals(b); }
    public static boolean matchesUser(long expected,long actual) { return expected>0 && expected==actual; }
    public static String moderationError(int status,String code) {
        if(status==422 && "NICKNAME_REJECTED".equals(code)) return "rejected";
        if(status==422 && ("NICKNAME_PENDING".equals(code) || "NICKNAME_MODERATION_PENDING".equals(code))) return "pending";
        if(status==503 && "NICKNAME_MODERATION_UNAVAILABLE".equals(code)) return "unavailable";
        if("NICKNAME_COOLDOWN".equals(code)) return "cooldown";
        if("INVALID_NICKNAME".equals(code)) return "invalid";
        return null;
    }
}
