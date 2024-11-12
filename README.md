# StatSave

StatSave is a Spigot plugin that allows you to save player statistics to a MySQL or MongoDB database. useful for serving player data to a website or Discord bot. It can also be used for other purposes, such as tracking player progress or creating leaderboards. Also has support for for saving PlaceholderAPI data.
<br>
This is an **EXPERIMENTAL** plugin. 
> **✨ Folia Supported**

> [!IMPORTANT]
> **This plugin is only for saving player data to a database, not syncing player data across servers.** If you're looking for a plugin that syncs player data, see [this](#sync-plugins).

## Installation
1. You can download the latest version of the plugin from [here](https://github.com/towsifkafi/StatSave/releases). (Latest release)
2. Put the jar file in your server's `plugins` folder.
3. Restart your server.
4. Start configuring the plugin by editing the `config.yml` file.
## Configuration
The plugin can be configured by editing the `config.yml` file. 
You can choose to save your data in either MySQL or MongoDB by modifying the `storage-method` value. Here's an example configuration:
```yml
# Method to save data. Set to 'MySQL' or 'MongoDB'
storage-method: MySQL

# MySQL Configuration
mysql:
  driver: com.mysql.cj.jdbc.MysqlDataSource
  # driver: com.mysql.jdbc.jdbc2.optional.MysqlDataSource
  host: 127.0.0.1
  port: 3306
  database: database
  username: user
  password: pass

# MongoDB Configuration
mongodb:
  connectionURI: mongodb://localhost:27017
  database: statsave
  collection: player_data

# Save custom placeholder data with PlaceholderAPI support. Set to false to disable.
placeholderapi:
  enabled: false
  placeholders:
    - "vault_eco_balance"

# rest of the config
```
## Usage
Once the plugin is installed and configured, it will automatically save player statistics to the database on player join and player leave event. You can then use a database client to access the data.
### Commands
- **/statsave** - `statsave.admin`
  - **/statsave reload** - Reloads the plugin and reconnects to the database.
  - **/statsave save** - Saves player stats of all active players.
  - **/statsave save offline** - Saves player stats of offline players. (*Unstable*) (*PlaceholderAPI Errors may occur*) (**Doesn't work on Folia**)

## Contributing
Contributions are welcome! Please feel free to submit pull requests or issues.
## Support
If you need help with the plugin or found any bug, You can contact me on discord [@towsif.kafi](https://discord.com/users/565102845926572033)

## Acknowledgements
- https://github.com/ld338/minecraft-mysql-sync
- https://github.com/brettwooldridge/HikariCP

## Sync Plugins
If you're searching for a plugin that synchronizes player data across multiple servers, consider trying these options:
- **HuskSync** - [SpigotMC](https://www.spigotmc.org/resources/husksync-1-17-1-21-sync-inventories-e-chests-x-server-advancements-map-art-stats-backups.97144/) - https://github.com/WiIIiam278/HuskSync
- **MySQL Sync** - [SpigotMC](https://www.spigotmc.org/resources/mysql-sync.101554/) - https://github.com/SphinxHD/MySQL-Sync
- **MySQL Player Data Bridge** - https://www.spigotmc.org/resources/mysql-player-data-bridge.8117/
- **IOSync** - https://modrinth.com/plugin/iosync

**Disclaimer:** I have no affiliation with the development of these plugins. I am only sharing information about them.