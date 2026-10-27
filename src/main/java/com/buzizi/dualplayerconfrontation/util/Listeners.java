package com.buzizi.dualplayerconfrontation.util;

import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

/** 卸载某个监听器实例（按实例注销其全部处理器） */
public final class Listeners {

    private Listeners() {
    }

    public static void unregister(Listener listener) {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }
    }
}
