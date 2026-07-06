#!/usr/bin/env python3
from personal_records.bootstrap import run_server
from personal_records.config import *
from personal_records.infrastructure.database import *
from personal_records.integrations.fatsecret_client import *
from personal_records.application.fatsecret_service import *
from personal_records.application.goodreads_service import *
from personal_records.application.letterboxd_service import *
from personal_records.presentation.api import *
from personal_records.presentation.html_views import *
from personal_records.presentation.http_server import Handler
from personal_records.shared import *


if __name__ == "__main__":
    run_server()
