package space.iseki.cmdpipe;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess;

@SuppressWarnings("unused")
class GraalvmFeature implements Feature {
    @Override
    public void duringSetup(DuringSetupAccess access) {
        var unnamedModule = this.getClass().getClassLoader().getUnnamedModule();
        RuntimeResourceAccess.addResource(unnamedModule, "space/iseki/cmdpipe/TextualFormatter.properties");
        RuntimeResourceAccess.addResource(unnamedModule, "space/iseki/cmdpipe/TextualFormatter_zh.properties");
    }
}
