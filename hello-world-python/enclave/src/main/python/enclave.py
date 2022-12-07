import random

random_value = random.randrange(1, 100)

print(random_value)

def receive_enclave_mail(mail):
    parts = mail.body.decode('utf-8').split()
    operator = parts[0]
    guess = int(parts[1])
    if operator == "=":
        response = "yes" if guess == random_value else "no"
    elif operator == "<":
        response = "yes" if random_value < guess else "no"
    else:
        response = "yes" if random_value > guess else "no"
    return response.encode('utf-8')