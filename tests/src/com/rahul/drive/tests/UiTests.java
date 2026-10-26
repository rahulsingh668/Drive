package com.rahul.drive.tests;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Requires an Android device; preserves the user's theme and Radio shortcut. */
public final class UiTests extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private Object field(Activity a, String name) throws Exception {
        Field f = a.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(a);
    }
    private void invoke(Activity a, String name, String value) throws Exception {
        Method m = a.getClass().getDeclaredMethod(name, String.class); m.setAccessible(true); m.invoke(a, value);
    }
    private void require(boolean value, String reason) { if(!value)throw new AssertionError(reason); }
    private boolean hasIcon(View view) {
        if(view instanceof ImageView)return ((ImageView)view).getDrawable()!=null;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(hasIcon(((ViewGroup)view).getChildAt(i)))return true;
        return false;
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Activity a = startActivitySync(new Intent().setClassName("com.rahul.drive","com.rahul.drive.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            final Throwable[] failure = new Throwable[1];
            runOnMainSync(() -> {
                SharedPreferences prefs = a.getSharedPreferences("drive",Context.MODE_PRIVATE);
                String oldMode = prefs.getString("theme_mode",null), oldRadio = prefs.getString("app.Radio",null);
                try {
                    invoke(a,"setThemeMode","day");
                    View surface=(View)field(a,"surface");
                    require(((ColorDrawable)surface.getBackground()).getColor()==Color.WHITE,"Day must have white background");
                    require(((TextView)field(a,"clock")).getCurrentTextColor()==Color.rgb(24,32,38),"Day clock must be dark");
                    require("day".equals(prefs.getString("theme_mode","")),"Manual day choice not saved");
                    invoke(a,"setThemeMode","night");
                    surface=(View)field(a,"surface");
                    require(((ColorDrawable)surface.getBackground()).getColor()==Color.rgb(17,20,22),"Night background incorrect");
                    invoke(a,"showApps",null);
                    GridView grid=(GridView)field(a,"appsGrid");
                    grid.measure(View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(550, View.MeasureSpec.EXACTLY));
                    require(grid!=null && grid.getNumColumns()==6,"Apps should use six-column grid");
                    require(grid.getAdapter().getCount()>0,"Expected installed apps");
                    View tile=grid.getAdapter().getView(0,null,grid);
                    require(hasIcon(tile),"App tile missing installed app icon");
                    require(tile.getContentDescription()!=null,"App tile missing accessible name");
                    Dialog before=(Dialog)field(a,"appsDialog");
                    invoke(a,"setThemeMode","day");
                    Dialog after=(Dialog)field(a,"appsDialog");
                    require(!before.isShowing() && after!=null && after.isShowing(),"Drawer should remain open after theme change");
                    invoke(a,"showApps","Radio"); grid=(GridView)field(a,"appsGrid");
                    tile=grid.getAdapter().getView(0,null,grid);
                    grid.performItemClick(tile,0,0);
                    require(prefs.getString("app.Radio",null)!=null,"Icon picker did not save shortcut");
                    invoke(a,"setThemeMode","auto");
                    require("auto".equals(prefs.getString("theme_mode","")),"Auto choice not saved");
                } catch(Throwable e) { failure[0]=e; }
                finally {
                    SharedPreferences.Editor restore=prefs.edit();
                    if(oldRadio==null)restore.remove("app.Radio");else restore.putString("app.Radio",oldRadio);
                    restore.apply();
                    try { invoke(a,"setThemeMode",oldMode==null?"auto":oldMode); } catch(Exception ignored) { }
                    if(oldMode==null)prefs.edit().remove("theme_mode").apply();
                }
            });
            if(failure[0]!=null)throw new RuntimeException(failure[0]);
            result.putString("result","PASS: light/dark palettes, saved modes, icon grid, drawer theme refresh and shortcut assignment. Preferences restored.");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("error",e.toString());finish(Activity.RESULT_CANCELED,result);}
    }
}
