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

    @Override
    public void releaseId() {
    }

    @Override
    public void load(ResourceManager resourceManager) {
    }
}
