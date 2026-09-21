module.exports = {
  apps: [
    {
      name: 'keyjolt',
      script: 'java',
      args: '-jar /home/zk/projects/KeyJolt/target/keyjolt-1.0.0.jar',
      interpreter: 'none',
      cwd: '/home/zk/projects/KeyJolt',
      env: {
        NODE_ENV: 'production',
      },
      watch: false,
      max_memory_restart: '600M',
      restart_delay: 5000,
      max_restarts: 10,
      out_file: '/home/zk/logs/keyjolt-out.log',
      error_file: '/home/zk/logs/keyjolt-err.log',
      merge_logs: true,
    },
  ],
};
