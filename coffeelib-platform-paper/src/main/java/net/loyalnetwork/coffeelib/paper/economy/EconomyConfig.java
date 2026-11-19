package net.loyalnetwork.coffeelib.paper.economy;

import net.loyalnetwork.coffeelib.api.config.annotation.Comment;
import net.loyalnetwork.coffeelib.api.config.annotation.ConfigFile;

/** CoffeeLib's {@code economy.yml}: how CoffeeLib currencies and Vault meet. */
@ConfigFile("economy")
public final class EconomyConfig {

    @Comment("The CoffeeLib currency offered to Vault plugins as the server's economy, such as coins. "
            + "Empty: none (Vault plugins keep whatever economy they have)")
    public String vaultExport = "";

    @Comment("The id another plugin's Vault economy (EssentialsX, CMI...) gets as a CoffeeLib currency, "
            + "so mods and plugins on CoffeeLib can use it. Empty: do not bring it in")
    public String vaultImport = "vault";
}
