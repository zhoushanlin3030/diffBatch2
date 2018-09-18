package com.kye;

public class UpdataEntity implements Comparable<UpdataEntity>{
	   	public String baseVersion = "";
	    public String md5Str = "";
	    public String downloadPath = "";

	    @Override
	    public int compareTo(UpdataEntity another) {
	        return -another.baseVersion.compareTo(this.baseVersion);
	    }
}
