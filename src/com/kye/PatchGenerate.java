package com.kye;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.gson.Gson;


public class PatchGenerate {
	
	private static String finCmd = "mshta vbscript:msgbox(\"新包出炉了!!!\",64,\"提示\")(window.close)";
	
	private static ConfigEntity configEntity;
	private static UpdateJsonBean updateJsonBean;
	
	public static void main(String[] arg) {
		
		//初始化
		initInfoData();
		
		//读取配置文件信息
		readConfig("GenerateConfig");
		
		//完整包信息的写入
		writeFullApkInfo();
		
		//获取要生成几个差分包
		countPatchNum();
		
		//开始生成差分包，创建新文件夹放入
		startGeneratepath();
		
		//检测合包
		checkMergeApk();
		
		//检测Json文件MD5
		checkMd5Again();
		
		//拷贝完整包到差分包文件夹
		copyFullApk();
		
		//生成txt版本文件
		creatTxtVersionFile();
		
		//zip压缩
		generateZipFile();

	}


	/**
	 * 初始化后面需要的实体
	 */
	private static void initInfoData() {
		configEntity = new ConfigEntity();
		updateJsonBean = new UpdateJsonBean();
		updateJsonBean.entities = new ArrayList<>();
	}
	
	/**
	 * 读取配置文件
	 * @param url
	 * @return
	 */
	private static void readConfig(String url) {
		Reader reader = null;
		BufferedReader br = null;
		StringBuffer strB = new StringBuffer();
		
		try {
			reader = new FileReader(new File(url));
			br = new BufferedReader(reader);
			String str = null;
			while((str = br.readLine()) != null) {
				strB.append(str);
			}
			Gson gson =new Gson();
			configEntity = gson.fromJson(strB.toString(), ConfigEntity.class);
			
		} catch (Exception e) {
			e.printStackTrace();
		}finally {
			if(reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			
			if(br!= null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * 计算生成几个差分包
	 * @param url
	 */
	private static void countPatchNum() {
		File file = new File(configEntity.patchFilesPath);
		File[] fileList = file.listFiles();
		File filePath = null;
		if(fileList != null && fileList.length > 0) {
			for(int i = 0;i < fileList.length;i++) {
				filePath = fileList[i];
				if(filePath.exists()) {
					UpdataEntity entity = new UpdataEntity();
					entity.baseVersion = filePath.getName().replace(".apk", "");
					entity.md5Str = "";
					entity.downloadPath = String.format("patch%d.apk", i+1);
					updateJsonBean.entities.add(entity);
				}
			}
		}
		System.out.println("☆☆差分包个数："+updateJsonBean.entities.size()+"个");
	}
	
	
	/**
	 * 写入完整包信息
	 */
	private static void writeFullApkInfo() {
		
		updateJsonBean.uploadVersion = configEntity.updataFilePath.replace(".apk", "");
		updateJsonBean.baseUrl = configEntity.baseUrl;
		updateJsonBean.functionName = configEntity.deviceName+".apk";
		updateJsonBean.isCompulsive = configEntity.isCompulsive;
		updateJsonBean.isConflict = configEntity.isConflict;
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				updateJsonBean.md5Str = getMD5(configEntity.updataFilePath);
			}
		}).start();
	} 
	
	
	/**
	 * 开始生成差分包
	 */
	private static void startGeneratepath() {
		
		List<String> filePaths = new ArrayList<String>();
		
		File baseApkFile = new File(configEntity.patchFilesPath);
		
		String zipName = configEntity.deviceName+configEntity.updataFilePath.replace(".apk","");
		
		File zipFile = new File(baseApkFile.getAbsoluteFile().getParent() +"\\"+ zipName);
		
		if(zipFile.exists()) {
			zipFile.delete();
		}else{
			zipFile.mkdir();
		}
		
		Runtime runtime = Runtime.getRuntime();
		System.out.println();
		System.out.println("开始生成差分包...");
		for(int i = 0;i < updateJsonBean.entities.size();i++) {
			try {
				UpdataEntity entity = updateJsonBean.entities.get(i);
				String patchApk = zipFile+"\\"+entity.downloadPath;
				File patchApkFile = new File(patchApk);
				
				filePaths.add(patchApk);
				
				if(patchApkFile.exists())patchApkFile.delete();
				
				String localApk = baseApkFile.getAbsolutePath()+"\\"+entity.baseVersion+".apk";
				
				String cmd = String.format("%s %s %s %s", configEntity.bsdiff,
						localApk, configEntity.updataFilePath,
						patchApk);
				System.out.println(cmd);
	
				Process p = runtime.exec(cmd);
				
				p.waitFor();

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		if(updateJsonBean.entities.size() == zipFile.listFiles().length) {
			System.out.println("差分包生成完毕!");
		}
		
	}
	
	/**
	 * 检测能否合包
	 */
	private static void checkMergeApk() {
		List<String> mergeFile = new ArrayList<String>();
		
		Runtime runtime = Runtime.getRuntime();
		
		File baseApkFile = new File(configEntity.patchFilesPath);
		
		String zipName = configEntity.deviceName+configEntity.updataFilePath.replace(".apk","");
		
		File zipFile = new File(baseApkFile.getAbsoluteFile().getParent() +"\\"+ zipName);
		
		if(zipFile.exists() && zipFile.listFiles().length > 0) {
			System.out.println();
			System.out.println("开始验证合包...");
			for(int i = 0; i< updateJsonBean.entities.size();i++) {
				
				System.out.println("合包"+(i+1));
				
				UpdataEntity entity = updateJsonBean.entities.get(i);
				
				String localApk = baseApkFile.getAbsolutePath()+"\\"+entity.baseVersion+".apk";
				
				String patchApk = zipFile.getAbsolutePath()+"\\"+entity.downloadPath;
				
				File mergeApk = new File(zipFile,String.format("new%d.apk", i+1));
				
				mergeFile.add(mergeApk.getAbsolutePath());
				
				if(mergeApk.exists()) {
					System.out.println("删了"+(i+1));
					mergeApk.delete();
				}
				
				String cmd = String.format("%s %s %s %s", configEntity.bspatch,
						localApk, mergeApk.getAbsolutePath(),
						patchApk);
				System.out.println(cmd);
	
				try {
					Process p = runtime.exec(cmd);
					p.waitFor();
				} catch (Exception e) {
					e.printStackTrace();
				}
				
			}
			
		}

		
		for (int i = 0; i < mergeFile.size(); i++) {
			File file = new File(mergeFile.get(i));
			String mergeMD5 = getMD5(file.getAbsolutePath());
			if(!updateJsonBean.md5Str.equals(mergeMD5)) {
				System.out.println("合包存在失败，请重新执行命令!");
				if(zipFile.exists())zipFile.delete();
				return;
			}
		}
		
		System.out.println("合包验证完成!");
		System.out.println();
		
		System.out.println("差分包MD5写入Json文件...");
		for(int i = 0;i < updateJsonBean.entities.size();i++) {
			UpdataEntity entity = updateJsonBean.entities.get(i);
			entity.md5Str = getMD5(zipFile.getAbsolutePath()+"\\"+entity.downloadPath);
			System.out.println(entity.downloadPath+"-->MD5值："+entity.md5Str);
		}
		
		
	}
	
	
	/**
	 * 写json文件
	 */
	private static void writeJsonFile(File jsonFile) {
		
		if(jsonFile.exists())jsonFile.delete();
		
		Writer w = null;
		
		try {
			
			Gson son = new Gson();
			
			w = new FileWriter(jsonFile, true);
			
			w.write(son.toJson(updateJsonBean));
			
			w.flush();
			
		} catch (IOException e) {
			e.printStackTrace();
		}finally {
			if(w != null) {
				try {
					w.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * 再次检测MD5
	 */
	private static void checkMd5Again() {
		
		File baseApkFile = new File(configEntity.patchFilesPath);
		
		String zipName = configEntity.deviceName+configEntity.updataFilePath.replace(".apk","");
		
		File zipFile = new File(baseApkFile.getAbsoluteFile().getParent() +"\\"+ zipName);
		
		for(int i = 0; i<updateJsonBean.entities.size();i++) {
			UpdataEntity entity = updateJsonBean.entities.get(i);
			
			String md5 = getMD5(zipFile.getAbsolutePath()+"\\"+entity.downloadPath);
			
			if(md5 != null && !"".equals(md5)) {
				if(!md5.equals(entity.md5Str)) {
					System.out.println(entity.baseVersion+"MD5值有误，请确认!");
				}
			}
		}
		System.out.println();
		System.out.println("★★检验MD5值OK，请手动确认或重复生成差分包对比每次生成是否一致后发包!★★");
		writeJsonFile(new File(zipFile,"PatchJson.txt"));
	}
	
	/**
	 * 生成txt 升级用文件
	 */
	private static void creatTxtVersionFile() {
		
		File baseApkFile = new File(configEntity.patchFilesPath);
		
		String zipName = configEntity.deviceName+configEntity.updataFilePath.replace(".apk","");
		
		File zipFile = new File(baseApkFile.getAbsoluteFile().getParent() +"\\"+ zipName);
		
        File file = new File(zipFile,"KYSYVersion.txt");
        if(!file.exists()){
            file.getParentFile().mkdirs();          
        }
        try {
        	
        	file.createNewFile();
            
            FileWriter fw = new FileWriter(file);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(configEntity.updataFilePath.replace(".apk","").replace(".", ""));
            bw.flush();
            bw.close();
            fw.close();
        	
        }catch(Exception e) {
        	
        }
        
	}

	
	/**
	 * 拷贝完整包到zip
	 */
	private static void copyFullApk() {
		File fullApkFile = new File(updateJsonBean.uploadVersion+".apk");
		
		File baseApkFile = new File(configEntity.patchFilesPath);
		
		String zipName = configEntity.deviceName+configEntity.updataFilePath.replace(".apk","");
		
		File zipFile = new File(baseApkFile.getAbsoluteFile().getParent() +"\\"+ zipName);
		
		File zipFullFile = new File(zipFile,configEntity.deviceName+".apk");
		
		if(fullApkFile.exists()) {
			try {
			        int length=2097152;
			        FileInputStream in=new FileInputStream(fullApkFile);
			        FileOutputStream out=new FileOutputStream(zipFullFile);
			        FileChannel inC=in.getChannel();
			        FileChannel outC=out.getChannel();
			        int i=0;
			        while(true){
			            if(inC.position()==inC.size()){
			                inC.close();
			                outC.close();			       
			            }
			            if((inC.size()-inC.position())<20971520)
			                length=(int)(inC.size()-inC.position());
			            else
			                length=20971520;
			            inC.transferTo(inC.position(),length,outC);
			            inC.position(inC.position()+length);
			            i++;
			        }
			} catch (Exception e) {
				
			}
		}
		
		String zipFullMd5 = getMD5(zipFullFile.getAbsolutePath());
		if(!updateJsonBean.md5Str.equals(zipFullMd5)) {
			System.out.println("压缩包中的完整包MD5有误,请确认!");
		}
	}
	
	/**
	 * 生成zip压缩包
	 */
	private static void generateZipFile() {
		
		File baseApkFile = new File(configEntity.patchFilesPath);
		
		String zipName = configEntity.deviceName+configEntity.updataFilePath.replace(".apk","");
		
		File zipFile = new File(baseApkFile.getAbsoluteFile().getParent() +"\\"+ zipName);
		
		try {
			boolean zipFinish = fileToZip(zipFile.getAbsolutePath(),baseApkFile.getAbsoluteFile().getParent(),configEntity.deviceName+configEntity.updataFilePath.replace(".apk", ""));
			if(zipFinish) {
				System.out.println();
				System.out.println("☆☆压缩包打好了!☆☆");
			}else {
				System.out.println();
				System.out.println("☆☆压缩包失败了!☆☆");
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}

		//详细信息显示
		System.out.println("****************MD5详细信息*************");
		System.out.println(" 完整包.apk-->MD5值："+updateJsonBean.md5Str);
		for(int i = 0;i < updateJsonBean.entities.size();i++) {
			UpdataEntity entity = updateJsonBean.entities.get(i);
			entity.md5Str = getMD5(zipFile.getAbsolutePath()+"\\"+entity.downloadPath);
			System.out.println(" "+entity.downloadPath+"-->MD5值："+entity.md5Str);
		}
		System.out.println("****************MD5详细信息*************");
		
		Runtime runtime = Runtime.getRuntime();
		
		try {
			runtime.exec(finCmd);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 压缩文件
	 * @param sourceFilePath
	 * @param zipFilePath
	 * @param fileName
	 * @return
	 */
	public static boolean fileToZip(String sourceFilePath,String zipFilePath,String fileName){  
        boolean flag = false;  
        File sourceFile = new File(sourceFilePath);  
        FileInputStream fis = null;  
        BufferedInputStream bis = null;  
        FileOutputStream fos = null;  
        ZipOutputStream zos = null;  
          
        if(!sourceFile.exists()){  
            System.out.println("待压缩的文件目录："+sourceFilePath+"不存在.");  
        }else{  
            try {  
                File zipFile = new File(zipFilePath + "/" + fileName +".zip");  
                if(zipFile.exists()){  
                    System.out.println(zipFilePath + "目录下存在名字为:" + fileName +".zip" +"打包文件.");  
                }else{  
                    File[] sourceFiles = sourceFile.listFiles();  
                    if(null == sourceFiles || sourceFiles.length<1){  
                        System.out.println("待压缩的文件目录：" + sourceFilePath + "里面不存在文件，无需压缩.");  
                    }else{  
                        fos = new FileOutputStream(zipFile);  
                        zos = new ZipOutputStream(new BufferedOutputStream(fos));  
                        byte[] bufs = new byte[1024*10];  
                        for(int i=0;i<sourceFiles.length;i++){
                        	if(!sourceFiles[i].getName().contains("new")){
	                            //创建ZIP实体，并添加进压缩包  
	                            ZipEntry zipEntry = new ZipEntry(sourceFiles[i].getName());  
	                            zos.putNextEntry(zipEntry);  
	                            //读取待压缩的文件并写进压缩包里  
	                            fis = new FileInputStream(sourceFiles[i]);  
	                            bis = new BufferedInputStream(fis, 1024*10);  
	                            int read = 0;  
	                            while((read=bis.read(bufs, 0, 1024*10)) != -1){  
	                                zos.write(bufs,0,read);  
	                            }  
                        	}
                        }  
                        flag = true;  
                    }  
                }  
            } catch (FileNotFoundException e) {  
                e.printStackTrace();  
                throw new RuntimeException(e);  
            } catch (IOException e) {  
                e.printStackTrace();  
                throw new RuntimeException(e);  
            } finally{  
                //关闭流  
                try {  
                    if(null != bis) bis.close();  
                    if(null != zos) zos.close();  
                } catch (IOException e) {  
                    e.printStackTrace();  
                    throw new RuntimeException(e);  
                }  
            }  
        }  
        return flag;  
    }  
	
	/**
	 * 获取MD5值
	 * @param fileUrl
	 * @return
	 */
	private static String getMD5(String fileUrl) {
		File file = new File(fileUrl);
		
        if (!file.isFile()) {
            return null;
        }
        MessageDigest digest = null;
        FileInputStream in = null;
        byte buffer[] = new byte[1024];
        int len;
        try {
            digest = MessageDigest.getInstance("MD5");
            in = new FileInputStream(file);
            while ((len = in.read(buffer, 0, 1024)) != -1) {
                digest.update(buffer, 0, len);
            }
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return bytesToHexString(digest.digest());
		
	}
	
	private static String bytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder("");
        if (src == null || src.length <= 0) {
            return null;
        }
        for (int i = 0; i < src.length; i++) {
            int v = src[i] & 0xFF;
            String hv = Integer.toHexString(v);
            if (hv.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hv);
        }
        return stringBuilder.toString();
    }


}
