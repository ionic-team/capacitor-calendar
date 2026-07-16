import { Calendar } from '@capacitor/calendar';

window.testEcho = () => {
    const inputValue = document.getElementById("echoInput").value;
    Calendar.echo({ value: inputValue })
}
