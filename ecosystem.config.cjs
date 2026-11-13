module.exports = {
  apps: [
    {
      name: 'keyjolt',
      script: '/usr/lib/jvm/java-17-openjdk-arm64/bin/java',
      args: '-jar /home/zk/projects/KeyJolt/target/keyjolt-1.1.5.jar --app.trusted-proxy=true',
      interpreter: 'none',
      cwd: '/home/zk/projects/KeyJolt',
      env: {
        NODE_ENV: 'production',
      },
      watch: false,
      max_memory_restart: '600M',
      // pm2 sends SIGINT and, by default, SIGKILL only 1.6 s later. The JVM needs
      // longer to stop cleanly; a SIGKILL skips the shutdown cleanup that deletes
      // pending key files (they would then only be removed at the next start).
      kill_timeout: 15000,
      restart_delay: 5000,
      max_restarts: 10,
      out_file: '/home/zk/logs/keyjolt-out.log',
      error_file: '/home/zk/logs/keyjolt-err.log',
      merge_logs: true,
    },
  ],
};
