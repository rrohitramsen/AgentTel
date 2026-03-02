import asyncio
import argparse
from .config.config import load_config
from .loop.orchestrator import MonitorOrchestrator


def main():
    parser = argparse.ArgumentParser(description='AgentTel Monitor')
    parser.add_argument('--config', default='monitor.yml', help='Config file path')
    args = parser.parse_args()
    config = load_config(args.config)
    orchestrator = MonitorOrchestrator(config)
    asyncio.run(orchestrator.run())


if __name__ == '__main__':
    main()
