package io.sapl.test.coverage.api;

import java.io.File;

import lombok.experimental.UtilityClass;

@UtilityClass
public class TestFileHelper {
	
	public static boolean deleteDirectory(File directoryToBeDeleted) {
	    File[] allContents = directoryToBeDeleted.listFiles();
	    if (allContents != null) {
	        for (File file : allContents) {
	            deleteDirectory(file);
	        }
	    }
	    return directoryToBeDeleted.delete();
	}

}
