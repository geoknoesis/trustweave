# Local test ledger only. Never use its deterministic genesis keys in a deployment.
FROM ghcr.io/bcgov/von-image:node-1.12-6@sha256:cf381fc523e59886e46ac530023fd5766b1b29e272ac72b3ef05136c87a7182a
USER indy
RUN python3 -m pip install --user --upgrade pip==21.3.1 && \
    python3 -m pip install --user --no-cache-dir indy-vdr==0.3.4 aiohttp==3.6.3 && \
    python3 -m pip install --user pip==9.0.3
USER root
COPY indy_config.py /etc/indy/indy_config.py
COPY gateway.py /home/indy/gateway.py
RUN mkdir -p /home/indy/ledger /home/indy/log /home/indy/backup /home/indy/plugins && chown indy:indy /etc/indy /etc/indy/indy_config.py /home/indy/ledger /home/indy/log /home/indy/backup /home/indy/plugins
USER indy
WORKDIR /home/indy
EXPOSE 8001
CMD ["python3", "gateway.py"]
