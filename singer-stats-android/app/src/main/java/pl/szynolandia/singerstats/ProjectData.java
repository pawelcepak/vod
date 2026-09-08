package pl.szynolandia.singerstats;

import org.json.*;
import java.util.*;

class Singer {
    int id; String name, color, imagePath;
    Singer(int id,String name,String color){this.id=id;this.name=name;this.color=color;}
    JSONObject json() throws JSONException { JSONObject o=new JSONObject();o.put("id",id);o.put("name",name);o.put("color",color);if(imagePath!=null)o.put("image",imagePath);return o; }
    static Singer from(JSONObject o){Singer s=new Singer(o.optInt("id",1),o.optString("name","Wokalista"),o.optString("color","#ffffff"));String x=o.optString("image","");s.imagePath=x.isEmpty()?null:x;return s;}
}

class LyricLine {
    String text=""; ArrayList<Integer> singerIds=new ArrayList<>(); Double start,end;
    JSONObject json() throws JSONException {JSONObject o=new JSONObject();o.put("text",text);JSONArray a=new JSONArray();for(int id:singerIds)a.put(id);o.put("singer_ids",a);if(!singerIds.isEmpty())o.put("singer_id",singerIds.get(0));o.put("start",start==null?JSONObject.NULL:start);o.put("end",end==null?JSONObject.NULL:end);return o;}
    static LyricLine from(JSONObject o,int fallback){LyricLine l=new LyricLine();l.text=o.optString("text","");JSONArray a=o.optJSONArray("singer_ids");if(a!=null)for(int i=0;i<a.length();i++)l.singerIds.add(a.optInt(i));if(l.singerIds.isEmpty())l.singerIds.add(o.optInt("singer_id",o.optInt("singerId",fallback)));if(o.has("start")&&!o.isNull("start"))l.start=o.optDouble("start");if(o.has("end")&&!o.isNull("end"))l.end=o.optDouble("end");return l;}
}

public class ProjectData {
    int version=5,nextSingerId=4; String audioPath,coverPath,lyricsBorderColor="#ff0050"; ArrayList<Singer>singers=new ArrayList<>();ArrayList<LyricLine>lyrics=new ArrayList<>();
    ProjectData(){singers.add(new Singer(1,"Wokalista 1","#ff0050"));singers.add(new Singer(2,"Wokalista 2","#00f2ea"));singers.add(new Singer(3,"Razem","#ffe600"));}
    JSONObject json() throws JSONException{JSONObject r=new JSONObject();r.put("version",version);r.put("audio_path",audioPath);r.put("next_singer_id",nextSingerId);JSONArray s=new JSONArray();for(Singer x:singers)s.put(x.json());r.put("singers",s);JSONArray l=new JSONArray();for(LyricLine x:lyrics)l.put(x.json());r.put("lyrics",l);JSONObject v=new JSONObject();v.put("cover_image",coverPath);v.put("lyrics_border_color",lyricsBorderColor);r.put("visual",v);return r;}
    static ProjectData from(JSONObject r){ProjectData p=new ProjectData();p.singers.clear();p.nextSingerId=r.optInt("next_singer_id",4);String a=r.optString("audio_path","");p.audioPath=a.isEmpty()?null:a;JSONArray ss=r.optJSONArray("singers");if(ss!=null)for(int i=0;i<ss.length();i++)p.singers.add(Singer.from(ss.optJSONObject(i)));if(p.singers.isEmpty())p.singers.add(new Singer(1,"Wokalista 1","#ff0050"));JSONObject v=r.optJSONObject("visual");if(v!=null){String c=v.optString("cover_image","");p.coverPath=c.isEmpty()?null:c;p.lyricsBorderColor=v.optString("lyrics_border_color","#ff0050");}int fallback=p.singers.get(0).id;JSONArray ll=r.optJSONArray("lyrics");if(ll!=null)for(int i=0;i<ll.length();i++)p.lyrics.add(LyricLine.from(ll.optJSONObject(i),fallback));return p;}
}
