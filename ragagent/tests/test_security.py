import jwt
import pytest
from fastapi import HTTPException

from ragagent.agent.policies import validate_tool_call
from ragagent.security.jwt_auth import JwtAuthenticator


def test_java_compatible_hs256_subject_is_accepted(settings):
    token = jwt.encode({"sub": "17", "exp": 4102444800}, settings.jwt_secret, algorithm="HS256")
    user = JwtAuthenticator(settings).authenticate(f"Bearer {token}")
    assert user.user_id == 17
    assert user.token == token


def test_invalid_token_is_rejected(settings):
    with pytest.raises(HTTPException) as raised:
        JwtAuthenticator(settings).authenticate("Bearer invalid")
    assert raised.value.status_code == 401


def test_tool_arguments_cannot_override_identity():
    with pytest.raises(ValueError, match="认证信息"):
        validate_tool_call("create_support_ticket", {"title": "x", "userId": 999})
