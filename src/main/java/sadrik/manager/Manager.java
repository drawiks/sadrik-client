package sadrik.manager;

import lombok.Getter;
import sadrik.client.draggables.HudManager;
import sadrik.command.CommandManager;
import sadrik.events.api.EventManager;
import sadrik.modules.impl.combat.aura.attack.StrikerConstructor;
import sadrik.modules.module.*;
import sadrik.screens.clickgui.ClickGui;
import sadrik.util.config.ConfigSystem;
import sadrik.util.config.impl.bind.BindConfig;
import sadrik.util.config.impl.blockesp.BlockESPConfig;
import sadrik.util.config.impl.drag.DragConfig;
import sadrik.util.config.impl.friend.FriendConfig;
import sadrik.util.config.impl.prefix.PrefixConfig;
import sadrik.util.config.impl.proxy.ProxyConfig;
import sadrik.util.config.impl.staff.StaffConfig;
import sadrik.util.modules.ModuleProvider;
import sadrik.util.modules.ModuleSwitcher;
import sadrik.util.render.shader.RenderCore;
import sadrik.util.render.shader.Scissor;
import sadrik.util.render.font.FontInitializer;
import sadrik.util.repository.macro.MacroRepository;
import sadrik.util.repository.way.WayRepository;
import sadrik.util.tps.TPSCalculate;

/**
 *  © 2026 Copyright Sadrik Client
 *        All Rights Reserved ®
 */

@Getter
public class Manager {
    public StrikerConstructor attackPerpetrator = new StrikerConstructor();
    private EventManager eventManager;
    private RenderCore renderCore;
    private Scissor scissor;
    private ModuleProvider moduleProvider;
    private ModuleRepository moduleRepository;
    private ModuleSwitcher moduleSwitcher;
    private ClickGui clickgui;
    private ConfigSystem configSystem;
    private CommandManager commandManager;
    private TPSCalculate tpsCalculate;
    private HudManager hudManager = new HudManager();

    public void init() {
        MacroRepository.getInstance().init();
        WayRepository.getInstance().init();
        BlockESPConfig.getInstance().load();
        FriendConfig.getInstance().load();
        PrefixConfig.getInstance().load();
        StaffConfig.getInstance().load();
        ProxyConfig.getInstance().load();
        DragConfig.getInstance().load();
        BindConfig.getInstance();

        FontInitializer.register();

        tpsCalculate = new TPSCalculate();

        clickgui = new ClickGui();
        eventManager = new EventManager();
        renderCore = new RenderCore();
        scissor = new Scissor();
        hudManager = new HudManager();
        hudManager.initElements();
        moduleRepository = new ModuleRepository();
        moduleRepository.setup();
        moduleProvider = new ModuleProvider(moduleRepository.modules());
        moduleSwitcher = new ModuleSwitcher(moduleRepository.modules(), eventManager);
        configSystem = new ConfigSystem();
        configSystem.init();
        commandManager = new CommandManager();
        commandManager.init();
    }
}