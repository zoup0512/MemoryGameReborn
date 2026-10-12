package com.cube.memorygames.api.local.model;

import android.text.TextUtils;
import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Table(name = "User")
public class LocalUser extends Model {
    @Column(name = "adsRemoved")
    public boolean adsRemoved;
    @Column(name = "authentication")
    public String authentication;
    @Column(name = "challengeRating")
    public float challengeRating;
    @Column(name = "country")
    public String country;
    @Column(name = "createdAt")
    public Date createdAt;
    @Column(name = "deepLinkSent")
    public boolean deepLinkSent;
    @Column(name = "displayName")
    public String displayName;
    @Column(name = "email")
    public String email;
    @Column(name = "facebookId")
    public String facebookId;
    @Column(name = "fromDeepLink")
    public boolean fromDeepLink;
    @Column(name = "gamesUnlocked")
    public boolean gamesUnlocked;
    @Column(name = "invitedBy")
    public String invitedBy;
    @Column(name = "money")
    public int money;
    @Column(name = "objectId")
    public String objectId;
    @Column(name = "onlineRank")
    public int onlineRank;
    @Column(name = "photoUrl")
    public String photoUrl;
    @Column(name = "rating")
    public float rating;
    @Column(name = "token")
    public String token;
    @Column(name = "unlimitedOnline")
    public boolean unlimitedOnline;
    @Column(name = "unlockedContent")
    public String unlockedContent;
    @Column(name = "updated")
    public boolean updated;
    @Column(name = "updatedAt")
    public Date updatedAt;
    @Column(name = "vip")
    public boolean vip;

    public List<String> parseUnlockedContent() {
        List<String> result = new ArrayList();
        if (!TextUtils.isEmpty(this.unlockedContent)) {
            Collections.addAll(result, this.unlockedContent.split(","));
        }
        return result;
    }

    public void addUnlockedContent(String content) {
        List<String> result = new ArrayList();
        if (!TextUtils.isEmpty(this.unlockedContent)) {
            Collections.addAll(result, this.unlockedContent.split(","));
        }
        if (!result.contains(content)) {
            if (TextUtils.isEmpty(this.unlockedContent)) {
                this.unlockedContent = content;
            } else {
                this.unlockedContent += "," + content;
            }
        }
        this.updated = true;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        this.updated = true;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
        this.updated = true;
    }
}
