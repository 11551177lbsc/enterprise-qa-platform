"""兼容旧启动命令；新项目统一从 ragagent.main 启动。"""

from ragagent.main import app

__all__ = ["app"]
