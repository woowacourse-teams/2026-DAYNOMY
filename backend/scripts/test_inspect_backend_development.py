import json
import unittest

from inspect_backend_development import startup_diagnostics


class StartupDiagnosticsTest(unittest.TestCase):
    def test_extracts_identifiers_without_log_values(self):
        message = (
            "org.springframework.beans.factory.BeanCreationException: "
            "Error creating bean with name 'sessionRepository': "
            "Could not resolve placeholder 'session.mode' in value secret-value; "
            'password=private-value user=user@example.com '
            'SQL State : 42P01 relation "spring_session" does not exist '
            'V98__shared_sessions.sql\n'
            'at com.example.Session.configure(Session.java:42)'
        )
        result = startup_diagnostics(json.dumps({'MESSAGE': message}) + '\ninvalid-json')
        self.assertEqual(['sessionRepository'], result['beans'])
        self.assertEqual(['session.mode'], result['properties'])
        self.assertEqual(['42P01'], result['sql_states'])
        self.assertEqual(['relation "spring_session" does not exist'], result['schema_errors'])
        self.assertEqual(['com.example.Session.configure(Session.java:42)'], result['frames'])
        for sensitive in ('secret-value', 'private-value', 'user@example.com', 'password='):
            self.assertNotIn(sensitive, json.dumps(result))

    def test_ignores_non_message_fields_and_non_string_messages(self):
        self.assertEqual({'reason_templates': []}, startup_diagnostics(json.dumps({'password': 'secret'})))
        self.assertEqual({'reason_templates': []}, startup_diagnostics(json.dumps({'MESSAGE': ['secret']})))


if __name__ == '__main__':
    unittest.main()
