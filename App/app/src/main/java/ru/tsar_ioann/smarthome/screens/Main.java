package ru.tsar_ioann.smarthome.screens;

import ru.tsar_ioann.smarthome.CommonData;

public class Main extends BaseScreen {
    public Main(CommonData commonData) {
        super(commonData);
    }

    @Override
    public int getViewFlipperChildId() {
        return 0;
    }

    @Override
    public boolean shouldMenuBeVisible() {
        return true;
    }
}
