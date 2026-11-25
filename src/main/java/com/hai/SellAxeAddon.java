package com.hai.sellaxe;

import com.hai.sellaxe.modules.AutoPayModule;
import com.hai.sellaxe.modules.SellAxeModule;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class SellAxeAddon extends MeteorAddon {
    public static final Category CATEGORY = new Category("SellAxe by Hải Trần");

    @Override
    public void onInitialize() {
        Modules.get().add(new SellAxeModule());
        Modules.get().add(new AutoPayModule());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.hai.sellaxe";
    }

    @Override
    public GithubRepo getRepo() {
        return null;
    }
}
