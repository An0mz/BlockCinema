package dev.anomz.blockcinema.client.browser;

import com.cinemamod.mcef.MCEFBrowser;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;

/** Lets a browser's picture be used like any other texture. MCEF owns the GL texture and deletes it on close. */
final class BrowserTexture extends AbstractTexture {
    private final MCEFBrowser browser;

    BrowserTexture(MCEFBrowser browser) {
        this.browser = browser;
    }

    @Override
    public int getId() {
        return browser.getRenderer().getTextureID();
    }

    /**
     * Always smooth. Minecraft's render types set their texture's filter every time they are used, and
     * the text one we draw with asks for sharp pixels, which makes video look blocky.
     */
    @Override
    public void setFilter(boolean blur, boolean mipmap) {
        super.setFilter(true, false);
    }

    @Override
    public void releaseId() {
    }

    @Override
    public void load(ResourceManager resourceManager) {
    }
}
