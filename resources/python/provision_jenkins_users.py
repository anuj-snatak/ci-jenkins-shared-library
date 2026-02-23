#!/usr/bin/env python3
"""
Production-grade Jenkins User Provisioning Script
"""

import csv
import os
import sys
import secrets
import string
import smtplib
import logging
import requests
import time
import re
from email.message import EmailMessage
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry


# ----------------------------------------------------------------------
# ENV CONFIG
# ----------------------------------------------------------------------
JENKINS_URL = os.environ.get('JENKINS_URL', 'http://localhost:8080').rstrip('/')
ADMIN_USER = os.environ.get('ADMIN_USER')
ADMIN_TOKEN = os.environ.get('ADMIN_TOKEN')

SMTP_SERVER = os.environ.get('SMTP_SERVER', 'smtp.gmail.com')
SMTP_PORT = int(os.environ.get('SMTP_PORT', 587))
SMTP_USER = os.environ.get('SMTP_USER')
SMTP_PASSWORD = os.environ.get('SMTP_PASSWORD')

CSV_PATH = os.environ.get('CSV_PATH', 'users.csv')


# ----------------------------------------------------------------------
# LOGGING
# ----------------------------------------------------------------------
logging.basicConfig(level=logging.INFO,
                    format="%(asctime)s - %(levelname)s - %(message)s")
logger = logging.getLogger("jenkins_provision")


# ----------------------------------------------------------------------
# HELPERS
# ----------------------------------------------------------------------
def generate_password(length=14):
    chars = string.ascii_letters + string.digits + "!@#$%^&*"
    return ''.join(secrets.choice(chars) for _ in range(length))


def session_with_retry():
    session = requests.Session()
    retry = Retry(total=3, backoff_factor=1,
                  status_forcelist=[500, 502, 503, 504])
    adapter = HTTPAdapter(max_retries=retry)
    session.mount("http://", adapter)
    session.mount("https://", adapter)
    return session


def validate_username(username):
    if not re.match(r'^[a-zA-Z0-9_\-]+$', username):
        raise ValueError(f"Invalid username: {username}")


def get_crumb():
    url = f"{JENKINS_URL}/crumbIssuer/api/json"
    try:
        r = session_with_retry().get(url,
                                     auth=(ADMIN_USER, ADMIN_TOKEN),
                                     timeout=10)
        if r.status_code == 200:
            data = r.json()
            return {data["crumbRequestField"]: data["crumb"]}
    except Exception:
        pass
    return {}


def user_exists(username):
    url = f"{JENKINS_URL}/securityRealm/user/{username}/api/json"
    r = session_with_retry().get(url,
                                 auth=(ADMIN_USER, ADMIN_TOKEN),
                                 timeout=10)
    return r.status_code == 200


def create_user(username, password, email):
    url = f"{JENKINS_URL}/securityRealm/createAccountByAdmin"
    data = {
        "username": username,
        "password1": password,
        "password2": password,
        "fullname": username,
        "email": email
    }
    data.update(get_crumb())

    r = session_with_retry().post(url,
                                  auth=(ADMIN_USER, ADMIN_TOKEN),
                                  data=data,
                                  allow_redirects=False,
                                  timeout=15)

    if r.status_code == 302:
        logger.info(f"User {username} created.")
        return True
    elif r.status_code == 200 and user_exists(username):
        logger.info(f"User {username} already exists.")
        return False
    else:
        raise RuntimeError(f"Failed creating {username}: {r.status_code}")


def assign_role(username, role):
    script = f"""
import jenkins.model.*
import com.michelin.cio.hudson.plugins.rolestrategy.*

def strategy = Jenkins.instance.getAuthorizationStrategy()
if (strategy instanceof RoleBasedAuthorizationStrategy) {{
    strategy.doAssignUserRole(RoleBasedAuthorizationStrategy.GLOBAL,
                              "{role}",
                              "{username}")
    Jenkins.instance.save()
    println "ASSIGNED"
}}
"""
    url = f"{JENKINS_URL}/scriptText"
    r = session_with_retry().post(url,
                                  auth=(ADMIN_USER, ADMIN_TOKEN),
                                  data={"script": script},
                                  timeout=15)
    if "ASSIGNED" not in r.text:
        raise RuntimeError(f"Role assignment failed for {username}")


def send_email(username, email, password, role):
    msg = EmailMessage()
    msg["Subject"] = "Your Jenkins Access"
    msg["From"] = SMTP_USER
    msg["To"] = email

    msg.set_content(f"""
Hello {username},

Your Jenkins account has been created.

URL: {JENKINS_URL}
Username: {username}
Password: {password}
Role: {role}

Please change password after login.
""")

    with smtplib.SMTP(SMTP_SERVER, SMTP_PORT) as server:
        server.starttls()
        server.login(SMTP_USER, SMTP_PASSWORD)
        server.send_message(msg)

    logger.info(f"Email sent to {email}")


# ----------------------------------------------------------------------
# MAIN
# ----------------------------------------------------------------------
def main():
    if not ADMIN_TOKEN:
        logger.error("ADMIN_TOKEN missing")
        sys.exit(1)

    with open(CSV_PATH, newline='', encoding="utf-8") as file:
        reader = csv.DictReader(file)

        for row in reader:
            username = row["username"].strip()
            email = row["email"].strip()
            role = row["role"].strip().lower()

            logger.info(f"Processing {username}")

            try:
                validate_username(username)

                if not user_exists(username):
                    password = generate_password()
                    created = create_user(username, password, email)
                    if created:
                        assign_role(username, role)
                        send_email(username, email, password, role)
                else:
                    logger.info(f"{username} already exists.")

            except Exception as e:
                logger.error(f"Error for {username}: {e}")

    logger.info("Provisioning completed.")


if __name__ == "__main__":
    main()
