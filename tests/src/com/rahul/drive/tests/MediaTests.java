package com.rahul.drive.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Parser and UI integration fixtures; never sends protected vendor broadcasts. */
public final class MediaTests extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args);start(); }
    private boolean contains(View v, String text) {
        if(v instanceof TextView && ((TextView)v).getText().toString().equals(text)) return true;
        if(v instanceof ViewGroup) for(int i=0;i<((ViewGroup)v).getChildCount();i++) if(contains(((ViewGroup)v).getChildAt(i),text))return true;
        return false;
    }
    private void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Activity activity = startActivitySync(new Intent().setClassName("com.rahul.drive","com.rahul.drive.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            final Throwable[] failure = new Throwable[1];
            runOnMainSync(() -> {
                Object adapter = null; Method receive = null;
                try {
                    Field field=activity.getClass().getDeclaredField("carLetterMedia");field.setAccessible(true);adapter=field.get(activity);
                    receive=adapter.getClass().getDeclaredMethod("onReceive",Context.class,Intent.class);receive.setAccessible(true);
                    receive.invoke(adapter,activity,new Intent("action.carletter.media.id3").putExtra("id3info","{\"title\":\"DRIVE test track\",\"artist\":\"Bridge test\"}"));
                    View root=activity.getWindow().getDecorView();
                    require(contains(root,"DRIVE test track"),"Title not rendered");
                    require(contains(root,"Bridge test"),"Artist not rendered");
                    require(contains(root,"ANDROID AUTO"),"Source not rendered");
                    receive.invoke(adapter,activity,new Intent("action.carletter.media.play.status").putExtra("playStatus",1).putExtra("playTime",84));
                    require(contains(root,"1:24"),"Position not rendered in seconds");
                    receive.invoke(adapter,activity,new Intent("action.carletter.media.id3").putExtra("id3info","not-json"));
                    require(contains(root,"DRIVE test track"),"Malformed data erased last valid metadata");
                    receive.invoke(adapter,activity,new Intent("action.carletter.media.id3").putExtra("id3info","{\"data\":{\"Track_Name\":\"Nested track\",\"Artist_Name\":\"Nested artist\"}}"));
                    require(contains(root,"Nested track")&&contains(root,"Nested artist"),"Nested/case-variant metadata not parsed");
                } catch(Throwable e) { failure[0]=e; }
                finally {
                    if(adapter!=null && receive!=null)try { receive.invoke(adapter,activity,new Intent("action.carletter.media.id3").putExtra("id3info","{\"title\":\"\"}")); } catch(Exception ignored) { }
                }
            });
            if(failure[0]!=null)throw new RuntimeException(failure[0]);
            result.putString("result","PASS: CarLetter metadata parser + title/artist/source UI, play position, malformed input, nested case variants; test metadata cleared. Protected broadcast delivery and live Android Auto require a real playback check.");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable e) { result.putString("error",e.toString());finish(Activity.RESULT_CANCELED,result); }
    }
}
