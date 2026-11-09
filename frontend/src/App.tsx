import { useEffect, useState } from "react";
import api from "./services/api";

function App() {
  const [message, setMessage] = useState("Connecting...");

  useEffect(() => {
    api.get("/hello")
      .then((response) => {
        setMessage(response.data);
      })
      .catch(() => {
        setMessage("Cannot connect to backend!");
      });
  }, []);

  return (
    <div>
      <h1>Find & Recommend Real Estate</h1>
      <p>{message}</p>
    </div>
  );
}

export default App;