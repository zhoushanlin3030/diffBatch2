package com.kye;

import java.util.Collections;
import java.util.List;

public class UpdateJsonBean {
	public List<UpdataEntity> entities;
    public String uploadVersion = "";
    public String md5Str = "";
    public String baseUrl ="";
    public String functionName = "";
    public boolean isConflict;
    public boolean isCompulsive;
    
    public void writeDownPath() {
    	Collections.sort(entities);
    	Collections.reverse(entities);
        for (int i = 0; i < entities.size(); i++) {
            entities.get(i).downloadPath = String.format("patch%d.apk", i+1);
        }
    }
}
