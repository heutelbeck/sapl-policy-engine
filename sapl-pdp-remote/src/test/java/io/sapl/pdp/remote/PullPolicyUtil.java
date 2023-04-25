package io.sapl.pdp.remote;

import org.testcontainers.images.AbstractImagePullPolicy;
import org.testcontainers.images.ImageData;
import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.utility.DockerImageName;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PullPolicyUtil {
	public ImagePullPolicy neverPull() {
		return new AbstractImagePullPolicy() {
            @Override
            protected boolean shouldPullCached(DockerImageName imageName, ImageData localImageData) {
                return false;
            }
            
            @Override
            public boolean shouldPull(DockerImageName imageName) {
            	return false;
            }
        };
	}
}
